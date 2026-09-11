package com.tf.reader.ingest.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tf.reader.admin.dto.AssetFormat;
import com.tf.reader.admin.dto.IngestStatus;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.WorkType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.PayloadTooLargeException;
import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.storage.StorageKeys;

import lombok.RequiredArgsConstructor;

/**
 * The synchronous half of ingest: validate the upload, stage its bytes in object storage, and
 * queue the one part it belongs to. Everything from there - locking, indexing, going READY or
 * FAILED - is {@link IngestProcessor}'s job, on its own scheduled tick, off this request thread.
 *
 * <p>{@code partNumber} defaults to 1 when absent, so a plain (non-chaptered) upload behaves
 * exactly as it always has: one asset, one part.
 */
@Service
@RequiredArgsConstructor
public class IngestService {

	private static final long GENERAL_MAX_BYTES = 100L * 1024 * 1024;
	private static final long LOCKED_MAX_BYTES = 25L * 1024 * 1024;
	private static final int DEFAULT_PART_NUMBER = 1;

	private final CatalogueItemRepository catalogueItemRepository;
	private final AdminScopeAuthorizer adminScope;
	private final AdminAuditWriter auditWriter;
	private final BookStorage bookStorage;
	private final Clock clock;

	public IngestStatus accept(String itemId, MultipartFile file, AssetFormat format, Integer partNumber,
			String partTitle) {
		CatalogueItem item = findOrThrow(itemId);
		requireAccess(item);
		requireLeaf(item);

		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "file is required");
		}
		if (format == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "format is required");
		}
		if (!format.name().equals(item.getContentType().name())) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"format must match this item's contentType (" + item.getContentType() + ")");
		}
		if (format == AssetFormat.AUDIO) {
			requireAudioExtension(file);
		}
		int resolvedPartNumber = partNumber == null ? DEFAULT_PART_NUMBER : partNumber;
		if (resolvedPartNumber < 1) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "partNumber must be 1 or greater");
		}

		boolean locked = TierRules.requiresLocking(item.getAccessTier(), item.getContentType());
		long limit = locked ? LOCKED_MAX_BYTES : GENERAL_MAX_BYTES;
		if (file.getSize() > limit) {
			throw new PayloadTooLargeException(
					locked ? "A file that will be locked may not exceed 25 MB" : "A file may not exceed 100 MB");
		}

		byte[] bytes = readBytes(file);
		ContentType contentType = item.getContentType();
		bookStorage.store(StorageKeys.staging(itemId, resolvedPartNumber), bytes, file.getContentType());

		Instant now = clock.instant();
		CatalogueItem.Asset asset = assetFor(item, contentType);
		CatalogueItem.Part part = upsertPart(asset, resolvedPartNumber, partTitle, now);
		part.setContentState(ContentState.QUEUED);
		part.setContentError(null);
		part.setUpdatedAt(now);

		item.setContentState(ContentState.QUEUED);
		item.setContentError(null);
		item.setUpdatedAt(now);
		catalogueItemRepository.save(item);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.INGEST, "CATALOGUE_ITEM", itemId, null,
				Map.of("format", format.name(), "partNumber", resolvedPartNumber, "sizeBytes", bytes.length));

		return toStatus(item, resolvedPartNumber, part);
	}

	public IngestStatus getStatus(String itemId, Integer partNumber) {
		CatalogueItem item = findOrThrow(itemId);
		requireAccess(item);
		int resolvedPartNumber = partNumber == null ? DEFAULT_PART_NUMBER : partNumber;
		CatalogueItem.Part part = findPart(item, resolvedPartNumber);
		return toStatus(item, resolvedPartNumber, part);
	}

	private IngestStatus toStatus(CatalogueItem item, int partNumber, CatalogueItem.Part part) {
		AssetFormat format = item.getContentType() == null ? null : AssetFormat.valueOf(item.getContentType().name());
		ContentState partState = part == null ? item.getContentState() : part.getContentState();
		String partError = part == null ? item.getContentError() : part.getContentError();
		Instant updatedAt = part == null ? item.getUpdatedAt() : part.getUpdatedAt();
		return new IngestStatus(item.getId(), format, partNumber, partState, partError, updatedAt);
	}

	/** Every existing item has exactly one asset for its one contentType - matched by format. */
	private static CatalogueItem.Asset assetFor(CatalogueItem item, ContentType format) {
		if (item.getAssets() != null) {
			for (CatalogueItem.Asset asset : item.getAssets()) {
				if (asset.getFormat() == format) {
					return asset;
				}
			}
		}
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(format);
		asset.setParts(new ArrayList<>());
		java.util.List<CatalogueItem.Asset> assets = new ArrayList<>(
				item.getAssets() == null ? java.util.List.of() : item.getAssets());
		assets.add(asset);
		item.setAssets(assets);
		return asset;
	}

	/** Upserts by partNumber, never touching a sibling part already in the list. */
	private static CatalogueItem.Part upsertPart(CatalogueItem.Asset asset, int partNumber, String partTitle,
			Instant now) {
		if (asset.getParts() == null) {
			asset.setParts(new ArrayList<>());
		}
		for (CatalogueItem.Part existing : asset.getParts()) {
			if (existing.getPartNumber() == partNumber) {
				if (partTitle != null) {
					existing.setTitle(partTitle);
				}
				return existing;
			}
		}
		CatalogueItem.Part part = new CatalogueItem.Part();
		part.setPartNumber(partNumber);
		part.setTitle(partTitle);
		asset.getParts().add(part);
		return part;
	}

	private static CatalogueItem.Part findPart(CatalogueItem item, int partNumber) {
		if (item.getAssets() == null) {
			return null;
		}
		for (CatalogueItem.Asset asset : item.getAssets()) {
			if (asset.getParts() == null) {
				continue;
			}
			for (CatalogueItem.Part part : asset.getParts()) {
				if (part.getPartNumber() == partNumber) {
					return part;
				}
			}
		}
		return null;
	}

	private CatalogueItem findOrThrow(String itemId) {
		return catalogueItemRepository.findById(itemId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such catalogue item"));
	}

	private void requireAccess(CatalogueItem item) {
		if (!adminScope.canAccessPublisher(item.getPublisherId())) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to access this book");
		}
	}

	/** A container (JOURNAL/VOLUME/ISSUE) never carries content of its own. */
	private static void requireLeaf(CatalogueItem item) {
		WorkType workType = item.getWorkType() == null ? WorkType.BOOK : item.getWorkType();
		if (workType != WorkType.BOOK && workType != WorkType.ARTICLE) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"a " + workType + " is a container and cannot have content uploaded to it");
		}
	}

	/**
	 * The filename's extension, not the client-supplied Content-Type header - a browser or curl
	 * caller can send any content type it likes, but it can't easily lie about what the file
	 * actually is without also lying about its own name.
	 */
	private static void requireAudioExtension(MultipartFile file) {
		String name = file.getOriginalFilename();
		String lower = name == null ? "" : name.toLowerCase();
		if (!lower.endsWith(".mp3") && !lower.endsWith(".wav")) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "audio format must be uploaded as .mp3 or .wav");
		}
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "file could not be read");
		}
	}

}
