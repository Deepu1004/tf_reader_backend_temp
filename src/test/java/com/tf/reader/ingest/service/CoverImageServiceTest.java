package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.PayloadTooLargeException;
import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.api.PresignedObject;

/** Cover art through the same BookStorage/signed-URL seam as the book file, but synchronous. */
class CoverImageServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final CatalogueItemRepository items = mock(CatalogueItemRepository.class);
	private final AdminScopeAuthorizer adminScope = mock(AdminScopeAuthorizer.class);
	private final AdminAuditWriter auditWriter = mock(AdminAuditWriter.class);
	private final BookStorage bookStorage = mock(BookStorage.class);
	private final CoverImageService service = new CoverImageService(items, adminScope, auditWriter, bookStorage,
			CLOCK);

	private static CatalogueItem item() {
		CatalogueItem item = new CatalogueItem();
		item.setId("item_42");
		item.setPublisherId("pub_rtlg");
		return item;
	}

	@Test
	void storesTheImageAndSavesThePresignedUrlAsTheCover() throws java.io.IOException {
		CatalogueItem item = item();
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));
		when(bookStorage.presign("items/item_42/cover", Duration.ofDays(7)))
				.thenReturn(new PresignedObject("https://b2.example/items/item_42/cover?sig=abc", NOW));
		MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[10]);

		String url = service.upload("item_42", file);

		assertThat(url).isEqualTo("https://b2.example/items/item_42/cover?sig=abc");
		assertThat(item.getCoverUrl()).isEqualTo(url);
		assertThat(item.getUpdatedAt()).isEqualTo(NOW);
		verify(bookStorage).store("items/item_42/cover", file.getBytes(), "image/jpeg");
	}

	@Test
	void rejectsAFileThatIsNotAnImage() {
		CatalogueItem item = item();
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.upload("item_42", file))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(bookStorage, never()).store(any(), any(), any());
	}

	@Test
	void aCoverOverFiveMegabytesIs413() {
		CatalogueItem item = item();
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile oversized = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[0]) {
			@Override
			public long getSize() {
				return 6L * 1024 * 1024;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}
		};

		assertThatExceptionOfType(PayloadTooLargeException.class)
				.isThrownBy(() -> service.upload("item_42", oversized))
				.withMessage("A cover image may not exceed 5 MB");
	}

	@Test
	void unknownItemIs404() {
		when(items.findById("item_nope")).thenReturn(Optional.empty());
		MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.upload("item_nope", file))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

}
