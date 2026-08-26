package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.tf.reader.admin.dto.AssetFormat;
import com.tf.reader.admin.dto.IngestStatus;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.PayloadTooLargeException;
import com.tf.reader.ingest.api.BookStorage;

/** The synchronous half of ingest: validation, size caps, staging, queueing. */
class IngestServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final CatalogueItemRepository items = mock(CatalogueItemRepository.class);
	private final AdminScopeAuthorizer adminScope = mock(AdminScopeAuthorizer.class);
	private final AdminAuditWriter auditWriter = mock(AdminAuditWriter.class);
	private final BookStorage bookStorage = mock(BookStorage.class);
	private final IngestService service = new IngestService(items, adminScope, auditWriter, bookStorage, CLOCK);

	private static CatalogueItem item(AccessTier tier, ContentType type) {
		CatalogueItem item = new CatalogueItem();
		item.setId("item_42");
		item.setPublisherId("pub_rtlg");
		item.setAccessTier(tier);
		item.setContentType(type);
		item.setContentState(ContentState.NONE);
		return item;
	}

	@Test
	void queuesAValidUploadAndReturns202Shape() throws java.io.IOException {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));
		MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[10]);

		IngestStatus status = service.accept("item_42", file, AssetFormat.PDF);

		assertThat(status.contentState()).isEqualTo(ContentState.QUEUED);
		assertThat(item.getContentState()).isEqualTo(ContentState.QUEUED);
		verify(bookStorage).store("items/item_42/upload", file.getBytes(), "application/pdf");
	}

	@Test
	void rejectsWhenFormatDoesNotMatchTheItemsContentType() {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile file = new MockMultipartFile("file", "book.epub", "application/epub+zip", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.accept("item_42", file, AssetFormat.EPUB))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(bookStorage, never()).store(any(), any(), any());
	}

	@Test
	void rejectsAnEmptyFile() {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile empty = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[0]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.accept("item_42", empty, AssetFormat.PDF))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void aFileOverTheGeneralCapIs413() {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.AUDIO);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile oversized = new MockMultipartFile("file", "book.mp3", "audio/mpeg", new byte[0]) {
			@Override
			public long getSize() {
				return 101L * 1024 * 1024;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}
		};

		assertThatExceptionOfType(PayloadTooLargeException.class)
				.isThrownBy(() -> service.accept("item_42", oversized, AssetFormat.AUDIO))
				.withMessageContaining("100 MB");
	}

	@Test
	void aFileThatWillBeLockedOverTwentyMegabytesIs413WithTheContractMessage() {
		CatalogueItem item = item(AccessTier.ELITE, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile oversized = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[0]) {
			@Override
			public long getSize() {
				return 21L * 1024 * 1024;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}
		};

		assertThatExceptionOfType(PayloadTooLargeException.class)
				.isThrownBy(() -> service.accept("item_42", oversized, AssetFormat.PDF))
				.withMessage("A file that will be locked may not exceed 20 MB");
	}

	@Test
	void unknownItemIs404() {
		when(items.findById("item_nope")).thenReturn(Optional.empty());
		MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.accept("item_nope", file, AssetFormat.PDF))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void getStatusReflectsTheItemsCurrentState() {
		CatalogueItem item = item(AccessTier.ELITE, ContentType.EPUB);
		item.setContentState(ContentState.FAILED);
		item.setContentError("boom");
		item.setUpdatedAt(NOW);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);

		IngestStatus status = service.getStatus("item_42");

		assertThat(status.format()).isEqualTo(AssetFormat.EPUB);
		assertThat(status.contentState()).isEqualTo(ContentState.FAILED);
		assertThat(status.contentError()).isEqualTo("boom");
		assertThat(status.updatedAt()).isEqualTo(NOW);
	}

}
