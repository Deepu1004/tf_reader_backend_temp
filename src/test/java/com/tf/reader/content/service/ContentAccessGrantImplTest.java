package com.tf.reader.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.content.api.ContentAccessGrant;
import com.tf.reader.content.api.ContentGrant;
import com.tf.reader.content.api.ContentGrantRequest;
import com.tf.reader.content.api.Format;
import com.tf.reader.content.api.Intent;
import com.tf.reader.content.api.LoanProof;
import com.tf.reader.crypto.api.BookEncryptionKeys;
import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.api.PresignedObject;

/**
 * Wiring only - {@code BookStorage} and {@code BookEncryptionKeys} are already tested on their
 * own (the storage client against real B2 semantics is untestable offline anyway; the crypto
 * primitives have their own unit tests). This proves {@code ContentAccessGrantImpl} reads the
 * right item fields, calls the right seam methods, and maps item state to the right grant shape
 * or the right {@link ApiException}.
 */
class ContentAccessGrantImplTest {

	private static final Instant EXPIRES = Instant.parse("2026-08-27T10:15:00Z");

	private final CatalogueItemRepository items = mock(CatalogueItemRepository.class);
	private final BookStorage bookStorage = mock(BookStorage.class);
	private final BookEncryptionKeys bookEncryptionKeys = mock(BookEncryptionKeys.class);
	private final EntitlementQuery entitlementQuery = mock(EntitlementQuery.class);
	private final ContentAccessGrant grant = new ContentAccessGrantImpl(items, bookStorage, bookEncryptionKeys,
			entitlementQuery);

	@BeforeEach
	void entitledByDefault() {
		when(entitlementQuery.check(any(), any()))
				.thenReturn(new EntitlementDecision(true, AccessLevel.ENTITLED_UNLIMITED, "ent_1", null, 14, null, null));
	}

	private static CatalogueItem readyItem(ContentType contentType, AccessTier tier, CatalogueItem.Asset asset,
			String indexKey) {
		CatalogueItem item = new CatalogueItem();
		item.setId("item_42");
		item.setContentType(contentType);
		item.setAccessTier(tier);
		item.setContentState(ContentState.READY);
		CatalogueItem.Part part = asset.getParts().get(0);
		part.setStorageKey("items/item_42/content");
		part.setIndexKey(indexKey);
		item.setAssets(List.of(asset));
		return item;
	}

	private static CatalogueItem.Asset unlockedAsset() {
		CatalogueItem.Part part = new CatalogueItem.Part();
		part.setPartNumber(1);
		part.setSizeBytes(1000L);
		part.setCipherLength(1000L);
		part.setContentState(ContentState.READY);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setMimeType("application/pdf");
		asset.setEncrypted(false);
		asset.setParts(List.of(part));
		return asset;
	}

	private static CatalogueItem.Asset lockedAsset() {
		CatalogueItem.Part part = new CatalogueItem.Part();
		part.setPartNumber(1);
		part.setSizeBytes(1000L);
		part.setCipherLength(1028L);
		part.setHasSearchIndex(true);
		part.setIndexTerms(42);
		part.setContentState(ContentState.READY);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setMimeType("application/pdf");
		asset.setEncrypted(true);
		asset.setKeyId("master-v1");
		asset.setMasterWrappedBek("master-wrapped-bek");
		asset.setParts(List.of(part));
		return asset;
	}

	private static ContentGrantRequest request(Format format, boolean wantSearchIndex, byte[] devicePublicKey) {
		return request("item_42", format, wantSearchIndex, devicePublicKey);
	}

	private static ContentGrantRequest request(String itemId, Format format, boolean wantSearchIndex,
			byte[] devicePublicKey) {
		return new ContentGrantRequest(itemId, format, Intent.STREAM, devicePublicKey,
				new SubjectRef("u_88", "inst_7f3"), new LoanProof("loan_88", Instant.parse("2099-01-01T00:00:00Z")),
				wantSearchIndex);
	}

	private static ContentGrantRequest requestForPart(int partNumber) {
		return new ContentGrantRequest("item_42", Format.PDF, Intent.STREAM, "device-key".getBytes(),
				new SubjectRef("u_88", "inst_7f3"), new LoanProof("loan_88", Instant.parse("2099-01-01T00:00:00Z")),
				false, partNumber);
	}

	@Test
	void aRequestForPartTwoPresignsThatPartAndRewrapsTheSharedBek() {
		CatalogueItem.Part part1 = new CatalogueItem.Part();
		part1.setPartNumber(1);
		part1.setContentState(ContentState.READY);
		part1.setStorageKey("items/item_42/content");
		CatalogueItem.Part part2 = new CatalogueItem.Part();
		part2.setPartNumber(2);
		part2.setContentState(ContentState.READY);
		part2.setStorageKey("items/item_42/part2/content");
		part2.setSizeBytes(2000L);
		part2.setCipherLength(2028L);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setMimeType("application/pdf");
		asset.setEncrypted(true);
		asset.setKeyId("master-v1");
		asset.setMasterWrappedBek("shared-wrapped-bek");
		asset.setParts(List.of(part1, part2));
		CatalogueItem item = new CatalogueItem();
		item.setId("item_42");
		item.setContentType(ContentType.PDF);
		item.setAccessTier(AccessTier.ELITE);
		item.setContentState(ContentState.READY);
		item.setAssets(List.of(asset));
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign("items/item_42/part2/content", Duration.ofMinutes(15)))
				.thenReturn(new PresignedObject("https://b2.example/part2?sig=1", EXPIRES));
		when(bookEncryptionKeys.rewrapForDevice(eq("shared-wrapped-bek"), any())).thenReturn("wrapped-for-device");

		ContentGrant result = grant.grant(requestForPart(2));

		assertThat(result.content().url()).isEqualTo("https://b2.example/part2?sig=1");
		assertThat(result.content().cipherLength()).isEqualTo(2028L);
		assertThat(result.encryption().wrappedBek()).isEqualTo("wrapped-for-device");
	}

	@Test
	void aPartThatIsNotItselfReadyIsContentNotReadyEvenWhenTheItemAggregateIsReady() {
		CatalogueItem.Part part1 = new CatalogueItem.Part();
		part1.setPartNumber(1);
		part1.setContentState(ContentState.READY);
		part1.setStorageKey("items/item_42/content");
		CatalogueItem.Part part2 = new CatalogueItem.Part();
		part2.setPartNumber(2);
		part2.setContentState(ContentState.PROCESSING);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setEncrypted(false);
		asset.setParts(List.of(part1, part2));
		CatalogueItem item = new CatalogueItem();
		item.setId("item_42");
		item.setContentType(ContentType.PDF);
		item.setAccessTier(AccessTier.OPEN_ACCESS);
		// The item's own aggregate is already READY - part 1 finished, part 2 hasn't yet.
		item.setContentState(ContentState.READY);
		item.setAssets(List.of(asset));
		when(items.findById("item_42")).thenReturn(Optional.of(item));

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> grant.grant(requestForPart(2)))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CONTENT_NOT_READY));
	}

	@Test
	void returnsASignedUrlForAnUnlockedAsset() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.OPEN_ACCESS, unlockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign("items/item_42/content", Duration.ofMinutes(15)))
				.thenReturn(new PresignedObject("https://b2.example/content?sig=1", EXPIRES));

		ContentGrant result = grant.grant(request(Format.PDF, true, null));

		assertThat(result.content().url()).isEqualTo("https://b2.example/content?sig=1");
		assertThat(result.content().expiresAt()).isEqualTo(EXPIRES);
		assertThat(result.content().cipherLength()).isEqualTo(1000L);
		assertThat(result.content().originalLength()).isEqualTo(1000L);
		assertThat(result.content().mimeType()).isEqualTo("application/pdf");
		assertThat(result.index()).isNull();
		assertThat(result.encryption()).isNull();
	}

	@Test
	void returnsSignedUrlIndexAndEncryptionForALockedAssetWithASearchIndex() throws Exception {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.ELITE, lockedAsset(), "items/item_42/index");
		byte[] devicePublicKey = "device-public-key-bytes".getBytes();
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign("items/item_42/content", Duration.ofMinutes(15)))
				.thenReturn(new PresignedObject("https://b2.example/content?sig=1", EXPIRES));
		when(bookStorage.presign("items/item_42/index", Duration.ofMinutes(15)))
				.thenReturn(new PresignedObject("https://b2.example/index?sig=2", EXPIRES));
		when(bookEncryptionKeys.rewrapForDevice(eq(item.getAssets().get(0).getMasterWrappedBek()), eq(devicePublicKey)))
				.thenReturn("wrapped-bek-for-device");

		ContentGrant result = grant.grant(request(Format.PDF, true, devicePublicKey));

		assertThat(result.content().url()).isEqualTo("https://b2.example/content?sig=1");
		assertThat(result.index().url()).isEqualTo("https://b2.example/index?sig=2");
		assertThat(result.index().encrypted()).isTrue();
		assertThat(result.index().termCount()).isEqualTo(42);

		assertThat(result.encryption().algorithm()).isEqualTo("AES-256-GCM");
		assertThat(result.encryption().layout()).isEqualTo("nonce(12) || ciphertext || tag(16)");
		assertThat(result.encryption().wrapAlgorithm()).isEqualTo("RSA-OAEP-256");
		assertThat(result.encryption().wrappedBek()).isEqualTo("wrapped-bek-for-device");
		assertThat(result.encryption().keyId()).isEqualTo("master-v1");
		assertThat(result.encryption().keyFingerprint()).isEqualTo(sha256Fingerprint(devicePublicKey));
	}

	@Test
	void omitsIndexWhenNotWanted() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.ELITE, lockedAsset(), "items/item_42/index");
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign(any(), any())).thenReturn(new PresignedObject("https://b2.example/x", EXPIRES));
		when(bookEncryptionKeys.rewrapForDevice(any(), any())).thenReturn("wrapped");

		ContentGrant result = grant.grant(request(Format.PDF, false, "device-key".getBytes()));

		assertThat(result.index()).isNull();
		verify(bookStorage, never()).presign(eq("items/item_42/index"), any());
	}

	@Test
	void omitsIndexWhenNoneWasBuilt() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.OPEN_ACCESS, unlockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign(any(), any())).thenReturn(new PresignedObject("https://b2.example/x", EXPIRES));

		ContentGrant result = grant.grant(request(Format.PDF, true, null));

		assertThat(result.index()).isNull();
	}

	@Test
	void unknownItemIsNotFound() {
		when(items.findById("item_nope")).thenReturn(Optional.empty());

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> grant.grant(request("item_nope", Format.PDF, false, null)))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void itemNotReadyIsContentNotReady() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.OPEN_ACCESS, unlockedAsset(), null);
		item.setContentState(ContentState.PROCESSING);
		when(items.findById("item_42")).thenReturn(Optional.of(item));

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> grant.grant(request(Format.PDF, false, null)))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CONTENT_NOT_READY));
	}

	@Test
	void missingDevicePublicKeyForEncryptedAssetIsRejected() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.ELITE, lockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> grant.grant(request(Format.PDF, false, null)))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_DEVICE_PUBLIC_KEY));
		verify(bookEncryptionKeys, never()).rewrapForDevice(any(), any());
	}

	@Test
	void propagatesInvalidDevicePublicKeyFromTheCryptoModule() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.ELITE, lockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign(any(), any())).thenReturn(new PresignedObject("https://b2.example/x", EXPIRES));
		when(bookEncryptionKeys.rewrapForDevice(any(), any()))
				.thenThrow(new ApiException(ErrorCode.INVALID_DEVICE_PUBLIC_KEY, "too small"));

		assertThatExceptionOfType(ApiException.class)
				.isThrownBy(() -> grant.grant(request(Format.PDF, false, "short-key".getBytes())))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_DEVICE_PUBLIC_KEY));
	}

	@Test
	void rejectsAnUnentitledSubject() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.OPEN_ACCESS, unlockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(entitlementQuery.check(any(), any()))
				.thenReturn(new EntitlementDecision(false, null, null, null, 0, null, DenyReason.ENTITLEMENT_EXPIRED));

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> grant.grant(request(Format.PDF, false, null)))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ENTITLEMENT_EXPIRED));
		verify(bookStorage, never()).presign(any(), any());
	}

	@Test
	void rejectsAMissingLoanProof() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.OPEN_ACCESS, unlockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		ContentGrantRequest request = new ContentGrantRequest("item_42", Format.PDF, Intent.STREAM, null,
				new SubjectRef("u_88", "inst_7f3"), null, false);

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> grant.grant(request))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NO_ACTIVE_LOAN));
	}

	@Test
	void rejectsAnExpiredLoanProof() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.OPEN_ACCESS, unlockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		ContentGrantRequest request = new ContentGrantRequest("item_42", Format.PDF, Intent.STREAM, null,
				new SubjectRef("u_88", "inst_7f3"), new LoanProof("loan_88", Instant.parse("2020-01-01T00:00:00Z")),
				false);

		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> grant.grant(request))
				.satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NO_ACTIVE_LOAN));
	}

	@Test
	void aLoanProofWithNoDueDateNeverExpires() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.ELITE, unlockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign(any(), any())).thenReturn(new PresignedObject("https://b2.example/x", EXPIRES));
		ContentGrantRequest request = new ContentGrantRequest("item_42", Format.PDF, Intent.STREAM, null,
				new SubjectRef("u_88", "inst_7f3"), new LoanProof("loan_88", null), false);

		ContentGrant result = grant.grant(request);

		assertThat(result.content().url()).isEqualTo("https://b2.example/x");
	}

	@Test
	void rejectsAMalformedRequest() {
		assertThatIllegalArgumentException().isThrownBy(() -> grant.grant(null));
		assertThatIllegalArgumentException().isThrownBy(
				() -> grant.grant(new ContentGrantRequest(" ", Format.PDF, Intent.STREAM, null, null, null, false)));
	}

	private static String sha256Fingerprint(byte[] devicePublicKey) throws java.security.NoSuchAlgorithmException {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(devicePublicKey);
		return "sha256:" + HexFormat.of().formatHex(digest);
	}

}
