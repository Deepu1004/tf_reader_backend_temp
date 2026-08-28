package com.tf.reader.ingest.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tf.reader.admin.dto.AssetFormat;
import com.tf.reader.admin.dto.IngestStatus;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
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
 * queue it. Everything from there - locking, indexing, going READY or FAILED - is
 * {@link IngestProcessor}'s job, on its own scheduled tick, off this request thread.
 */
@Service
@RequiredArgsConstructor
public class IngestService {

	private static final long GENERAL_MAX_BYTES = 100L * 1024 * 1024;
	private static final long LOCKED_MAX_BYTES = 25L * 1024 * 1024;

	private final CatalogueItemRepository catalogueItemRepository;
	private final AdminScopeAuthorizer adminScope;
	private final AdminAuditWriter auditWriter;
	private final BookStorage bookStorage;
	private final Clock clock;

	public IngestStatus accept(String itemId, MultipartFile file, AssetFormat format) {
		CatalogueItem item = findOrThrow(itemId);
		requireAccess(item);

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

		boolean locked = TierRules.requiresLocking(item.getAccessTier(), item.getContentType());
		long limit = locked ? LOCKED_MAX_BYTES : GENERAL_MAX_BYTES;
		if (file.getSize() > limit) {
			throw new PayloadTooLargeException(
					locked ? "A file that will be locked may not exceed 25 MB" : "A file may not exceed 100 MB");
		}

		byte[] bytes = readBytes(file);
		bookStorage.store(StorageKeys.staging(itemId), bytes, file.getContentType());

		Instant now = clock.instant();
		item.setContentState(ContentState.QUEUED);
		item.setContentError(null);
		item.setUpdatedAt(now);
		catalogueItemRepository.save(item);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.INGEST, "CATALOGUE_ITEM", itemId, null,
				Map.of("format", format.name(), "sizeBytes", bytes.length));

		return toStatus(item);
	}

	public IngestStatus getStatus(String itemId) {
		CatalogueItem item = findOrThrow(itemId);
		requireAccess(item);
		return toStatus(item);
	}

	private IngestStatus toStatus(CatalogueItem item) {
		AssetFormat format = item.getContentType() == null ? null : AssetFormat.valueOf(item.getContentType().name());
		return new IngestStatus(item.getId(), format, item.getContentState(), item.getContentError(),
				item.getUpdatedAt());
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

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException e) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "file could not be read");
		}
	}

}
