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
import com.tf.reader.catalogue.entity.WorkType;
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
		item.setWorkType(WorkType.BOOK);
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

		IngestStatus status = service.accept("item_42", file, AssetFormat.PDF, null, null);

		assertThat(status.contentState()).isEqualTo(ContentState.QUEUED);
		assertThat(status.partNumber()).isEqualTo(1);
		assertThat(item.getContentState()).isEqualTo(ContentState.QUEUED);
		verify(bookStorage).store("items/item_42/upload", file.getBytes(), "application/pdf");
	}

	@Test
	void explicitPartNumberAndTitleUpsertTheRightPartWithoutTouchingASibling() throws java.io.IOException {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.PDF);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		CatalogueItem.Part existingPart1 = new CatalogueItem.Part();
		existingPart1.setPartNumber(1);
		existingPart1.setContentState(ContentState.READY);
		asset.setParts(new java.util.ArrayList<>(java.util.List.of(existingPart1)));
		item.setAssets(java.util.List.of(asset));
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));
		MockMultipartFile file = new MockMultipartFile("file", "ch2.pdf", "application/pdf", new byte[10]);

		IngestStatus status = service.accept("item_42", file, AssetFormat.PDF, 2, "Background");

		assertThat(status.partNumber()).isEqualTo(2);
		assertThat(asset.getParts()).hasSize(2);
		assertThat(existingPart1.getContentState()).isEqualTo(ContentState.READY);
		verify(bookStorage).store("items/item_42/part2/upload", file.getBytes(), "application/pdf");
	}

	@Test
	void noPartNumberDefaultsToOneMatchingTodaysBehaviourExactly() throws java.io.IOException {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));
		MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[10]);

		IngestStatus status = service.accept("item_42", file, AssetFormat.PDF, null, null);

		assertThat(status.partNumber()).isEqualTo(1);
		verify(bookStorage).store("items/item_42/upload", file.getBytes(), "application/pdf");
	}

	@Test
	void partNumberBelowOneIsRejected() {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.accept("item_42", file, AssetFormat.PDF, 0, null))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(bookStorage, never()).store(any(), any(), any());
	}

	@Test
	void uploadingToAContainerIsRejected() {
		CatalogueItem journal = new CatalogueItem();
		journal.setId("item_journal");
		journal.setPublisherId("pub_rtlg");
		journal.setWorkType(WorkType.JOURNAL);
		when(items.findById("item_journal")).thenReturn(Optional.of(journal));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.accept("item_journal", file, AssetFormat.PDF, null, null))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void rejectsWhenFormatDoesNotMatchTheItemsContentType() {
		CatalogueItem item = item(AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile file = new MockMultipartFile("file", "book.epub", "application/epub+zip", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.accept("item_42", file, AssetFormat.EPUB, null, null))
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
				.isThrownBy(() -> service.accept("item_42", empty, AssetFormat.PDF, null, null))
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
				.isThrownBy(() -> service.accept("item_42", oversized, AssetFormat.AUDIO, null, null))
				.withMessageContaining("100 MB");
	}

	@Test
	void aFileThatWillBeLockedOverTwentyFiveMegabytesIs413WithTheContractMessage() {
		CatalogueItem item = item(AccessTier.ELITE, ContentType.PDF);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile oversized = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[0]) {
			@Override
			public long getSize() {
				return 26L * 1024 * 1024;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}
		};

		assertThatExceptionOfType(PayloadTooLargeException.class)
				.isThrownBy(() -> service.accept("item_42", oversized, AssetFormat.PDF, null, null))
				.withMessage("A file that will be locked may not exceed 25 MB");
	}

	@Test
	void lockedAudioIsAlsoCappedAtTwentyFiveMegabytesNotTheGeneralCap() {
		CatalogueItem item = item(AccessTier.ELITE, ContentType.AUDIO);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		MockMultipartFile oversized = new MockMultipartFile("file", "book.mp3", "audio/mpeg", new byte[0]) {
			@Override
			public long getSize() {
				return 26L * 1024 * 1024;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}
		};

		assertThatExceptionOfType(PayloadTooLargeException.class)
				.isThrownBy(() -> service.accept("item_42", oversized, AssetFormat.AUDIO, null, null))
				.withMessage("A file that will be locked may not exceed 25 MB");
	}

	@Test
	void lockedAudioWithinTheCapQueuesLikeAnyOtherLockedAsset() throws java.io.IOException {
		CatalogueItem item = item(AccessTier.SUBSCRIPTION, ContentType.AUDIO);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));
		MockMultipartFile file = new MockMultipartFile("file", "book.mp3", "audio/mpeg", new byte[10]);

		IngestStatus status = service.accept("item_42", file, AssetFormat.AUDIO, null, null);

		assertThat(status.contentState()).isEqualTo(ContentState.QUEUED);
		verify(bookStorage).store("items/item_42/upload", file.getBytes(), "audio/mpeg");
	}

	@Test
	void unknownItemIs404() {
		when(items.findById("item_nope")).thenReturn(Optional.empty());
		MockMultipartFile file = new MockMultipartFile("file", "book.pdf", "application/pdf", new byte[10]);

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> service.accept("item_nope", file, AssetFormat.PDF, null, null))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void getStatusReflectsThePartsCurrentState() {
		CatalogueItem item = item(AccessTier.ELITE, ContentType.EPUB);
		item.setContentState(ContentState.FAILED);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.EPUB);
		CatalogueItem.Part part = new CatalogueItem.Part();
		part.setPartNumber(1);
		part.setContentState(ContentState.FAILED);
		part.setContentError("boom");
		part.setUpdatedAt(NOW);
		asset.setParts(java.util.List.of(part));
		item.setAssets(java.util.List.of(asset));
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(adminScope.canAccessPublisher("pub_rtlg")).thenReturn(true);

		IngestStatus status = service.getStatus("item_42", null);

		assertThat(status.format()).isEqualTo(AssetFormat.EPUB);
		assertThat(status.partNumber()).isEqualTo(1);
		assertThat(status.contentState()).isEqualTo(ContentState.FAILED);
		assertThat(status.contentError()).isEqualTo("boom");
		assertThat(status.updatedAt()).isEqualTo(NOW);
	}

}
