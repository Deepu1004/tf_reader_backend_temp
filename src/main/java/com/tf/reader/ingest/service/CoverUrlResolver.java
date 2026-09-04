package com.tf.reader.ingest.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.ingest.api.BookStorage;

import lombok.RequiredArgsConstructor;

/**
 * Resolves a book's cover to a URL a reader can actually fetch, the same way
 * {@code ContentAccessGrantImpl} resolves the book file itself: fresh on every read, never
 * persisted, because the bucket is private and a signed URL is not durable.
 *
 * <p>A cover uploaded through {@link CoverImageService} carries a {@code coverKey} and is
 * presigned here. A cover set by pasting an external link carries no key and is returned
 * exactly as stored - that link is the operator's to keep working, not ours to sign.
 */
@Component
@RequiredArgsConstructor
public class CoverUrlResolver {

	private static final Duration URL_TTL = Duration.ofDays(7);

	private final BookStorage bookStorage;

	public String resolve(CatalogueItem item) {
		String coverKey = item.getCoverKey();
		if (coverKey == null) {
			return item.getCoverUrl();
		}
		return bookStorage.presign(coverKey, URL_TTL).url();
	}

}
