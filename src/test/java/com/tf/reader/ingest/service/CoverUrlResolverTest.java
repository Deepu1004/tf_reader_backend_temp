package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.api.PresignedObject;

/** Fresh on every read, never persisted - mirrors how ContentAccessGrantImpl resolves the book file. */
class CoverUrlResolverTest {

	private final BookStorage bookStorage = mock(BookStorage.class);
	private final CoverUrlResolver resolver = new CoverUrlResolver(bookStorage);

	@Test
	void presignsFreshWhenTheCoverWasUploaded() {
		CatalogueItem item = new CatalogueItem();
		item.setCoverKey("items/item_42/cover");
		when(bookStorage.presign("items/item_42/cover", Duration.ofDays(7)))
				.thenReturn(new PresignedObject("https://b2.example/items/item_42/cover?sig=abc", Instant.now()));

		assertThat(resolver.resolve(item)).isEqualTo("https://b2.example/items/item_42/cover?sig=abc");
	}

	@Test
	void returnsThePastedInLinkAsIsWhenNoCoverWasUploaded() {
		CatalogueItem item = new CatalogueItem();
		item.setCoverUrl("https://cdn.tf/covers/item_1.jpg");

		assertThat(resolver.resolve(item)).isEqualTo("https://cdn.tf/covers/item_1.jpg");
	}

}
