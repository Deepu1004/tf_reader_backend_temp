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

import org.junit.jupiter.api.Test;

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
	private final ContentAccessGrant grant = new ContentAccessGrantImpl(items, bookStorage, bookEncryptionKeys);

	private static CatalogueItem readyItem(ContentType contentType, AccessTier tier, CatalogueItem.Asset asset,
			String indexKey) {
		CatalogueItem item = new CatalogueItem();
		item.setId("item_42");
		item.setContentType(contentType);
		item.setAccessTier(tier);
		item.setContentState(ContentState.READY);
		item.setStorageKey("items/item_42/content");
		item.setIndexKey(indexKey);
		item.setAssets(List.of(asset));
		return item;
	}

	private static CatalogueItem.Asset unlockedAsset() {
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setMimeType("application/pdf");
		asset.setSizeBytes(1000L);
		asset.setCipherLength(1000L);
		asset.setEncrypted(false);
		return asset;
	}

	private static CatalogueItem.Asset lockedAsset() {
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setMimeType("application/pdf");
		asset.setSizeBytes(1000L);
		asset.setCipherLength(1028L);
		asset.setEncrypted(true);
		asset.setHasSearchIndex(true);
		asset.setIndexTerms(42);
		asset.setKeyId("master-v1");
		return asset;
	}

	private static ContentGrantRequest request(Format format, boolean wantSearchIndex, byte[] devicePublicKey) {
		return request("item_42", format, wantSearchIndex, devicePublicKey);
	}

	private static ContentGrantRequest request(String itemId, Format format, boolean wantSearchIndex,
			byte[] devicePublicKey) {
		return new ContentGrantRequest(itemId, format, Intent.STREAM, devicePublicKey,
				new SubjectRef("u_88", "inst_7f3"), new LoanProof("loan_88", Instant.parse("2026-08-21T10:00:00Z")),
				wantSearchIndex);
	}

	@Test
	void returnsASignedUrlForAnUnlockedAsset() {
		CatalogueItem item = readyItem(ContentType.PDF, AccessTier.OPEN_ACCESS, unlockedAsset(), null);
		when(items.findById("item_42")).thenReturn(Optional.of(item));
		when(bookStorage.presign("items/item_42/content", Duration.ofMinutes(15)))
				.thenReturn(new PresignedObject("https://b2.example/content?sig=1", EXPIRES));

		ContentGrant result = grant.grant(request(Format.PDF, true, null));

        assertThat(result.content().url()).startsWith("http://localhost:8080/mock-content/");
        assertThat(result.content().expiresAt()).isAfter(Instant.now());
        assertThat(result.content().mimeType()).isEqualTo("application/pdf");
        assertThat(result.content().cipherLength()).isPositive();
        assertThat(result.content().originalLength()).isPositive();

        // "item_c25" has no index fixture (it falls back to the big, un-indexed PDF), so the
        // index is correctly absent here — see the dedicated test below for the itemId that has
        // one, which is where index().url() actually gets asserted against a real fixture.
        assertThat(result.index()).isNull();

        assertThat(result.encryption().algorithm()).isEqualTo("AES-256-GCM");
        assertThat(result.encryption().wrapAlgorithm()).isEqualTo("RSA-OAEP-256");
        assertThat(unwrap(result.encryption().wrappedBek(), deviceKey.getPrivate()))
                .isEqualTo(Base64.getDecoder().decode("hvVWs7CKbTSCYXSFQmUtOIOLYe7cjeZgilJ16YpKdB0="));
    }

    // Pins the fix for the bug this test used to assert as correct behaviour: `index().url()`
    // reusing `content().url()` verbatim, so a client decrypting the "index" actually got the
    // book's own ciphertext and failed to parse it as index JSON (utf8Decode, confirmed on-device
    // and via direct curl, 2026-09-01). "dev-sample-pdf" is one of the two itemIds with a real,
    // separately-encrypted index fixture (see EPUB_SMALL_INDEX_FIXTURE's comment) — the two URLs
    // must now be DIFFERENT files.
    @Test
    void theIndexUrlIsARealIndexFixtureNotTheContentUrl() {
        ContentGrant result = grant.grant(
                new ContentGrantRequest(
                        "dev-sample-pdf",
                        Format.PDF,
                        Intent.STREAM,
                        deviceKeyPair().getPublic().getEncoded(),
                        new SubjectRef("u_88", "inst_7f3"),
                        new LoanProof("loan_88", Instant.parse("2026-08-21T10:00:00Z")),
                        true
                ));

        assertThat(result.index()).isNotNull();
        assertThat(result.index().url()).isNotEqualTo(result.content().url());
        assertThat(result.index().url()).endsWith("sample-small.pdf.index.enc");
        assertThat(result.index().termCount()).isPositive();
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
