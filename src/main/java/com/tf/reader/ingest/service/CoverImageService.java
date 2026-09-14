package com.tf.reader.ingest.service;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.CatalogueItem;
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
 * Cover art through the same {@link BookStorage} seam as the book file itself, but synchronous -
 * there is no encryption and no {@link IngestProcessor} step. Only the storage key is persisted,
 * never a URL: the bucket is private, so a presigned link expires, and {@link CoverUrlResolver}
 * is what turns the key back into a working URL on every read.
 */
@Service
@RequiredArgsConstructor
public class CoverImageService {

	private static final long MAX_BYTES = 5L * 1024 * 1024;

	private final CatalogueItemRepository catalogueItemRepository;
	private final AdminScopeAuthorizer adminScope;
	private final AdminAuditWriter auditWriter;
	private final BookStorage bookStorage;
	private final Clock clock;

	public CatalogueItem upload(String itemId, MultipartFile file) {
		CatalogueItem item = findOrThrow(itemId);
		requireAccess(item);

		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "file is required");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "file must be an image");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new PayloadTooLargeException("A cover image may not exceed 5 MB");
		}

		String key = StorageKeys.cover(itemId);
		bookStorage.store(key, readBytes(file), contentType);

		item.setCoverKey(key);
		item.setCoverMimeType(contentType);
		item.setUpdatedAt(clock.instant());
		CatalogueItem saved = catalogueItemRepository.save(item);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.UPDATE, "CATALOGUE_ITEM", itemId, null,
				Map.of("coverUploaded", true));

		return saved;
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
