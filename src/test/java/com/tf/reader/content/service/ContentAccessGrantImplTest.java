package com.tf.reader.content.service;

import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.content.api.ContentAccessGrant;
import com.tf.reader.content.api.ContentGrant;
import com.tf.reader.content.api.ContentGrantRequest;
import com.tf.reader.content.api.Format;
import com.tf.reader.content.api.Intent;
import com.tf.reader.content.api.LoanProof;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentAccessGrantImplTest {

    private final ContentAccessGrant grant = new ContentAccessGrantImpl("http://localhost:8080");

    private static KeyPair deviceKeyPair() {
        try {
            return KeyPairGenerator.getInstance("RSA").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ContentGrantRequest request(Format format, boolean wantSearchIndex, byte[] devicePublicKey) {
        return new ContentGrantRequest(
                "item_c25",
                format,
                Intent.STREAM,
                devicePublicKey,
                new SubjectRef("u_88", "inst_7f3"),
                new LoanProof("loan_88", Instant.parse("2026-08-21T10:00:00Z")),
                wantSearchIndex
        );
    }

    @Test
    void returnsAContentLinkAndAKeyTheDeviceCanActuallyUnwrap() throws Exception {
        KeyPair deviceKey = deviceKeyPair();
        ContentGrant result = grant.grant(
                request(Format.PDF, true, deviceKey.getPublic().getEncoded()));

        assertThat(result.content().url()).startsWith("http://localhost:8080/mock-content/");
        assertThat(result.content().expiresAt()).isAfter(Instant.now());
        assertThat(result.content().mimeType()).isEqualTo("application/pdf");
        assertThat(result.content().cipherLength()).isPositive();
        assertThat(result.content().originalLength()).isPositive();

        assertThat(result.index()).isNotNull();
        assertThat(result.index().url()).isEqualTo(result.content().url());

        assertThat(result.encryption().algorithm()).isEqualTo("AES-256-GCM");
        assertThat(result.encryption().wrapAlgorithm()).isEqualTo("RSA-OAEP-256");
        assertThat(unwrap(result.encryption().wrappedBek(), deviceKey.getPrivate()))
                .isEqualTo(Base64.getDecoder().decode("hvVWs7CKbTSCYXSFQmUtOIOLYe7cjeZgilJ16YpKdB0="));
    }

    @Test
    void omitsTheIndexWhenItWasNotAskedFor() {
        assertThat(grant.grant(request(Format.PDF, false, deviceKeyPair().getPublic().getEncoded())).index())
                .isNull();
    }

    @Test
    void omitsEncryptionForAudioBecauseWholeFileEncryptionCannotSeek() {
        ContentGrant result = grant.grant(request(Format.AUDIO, true, deviceKeyPair().getPublic().getEncoded()));

        assertThat(result.encryption()).isNull();
        assertThat(result.index()).isNull();
    }

    @Test
    void rejectsAMalformedRequest() {
        assertThatIllegalArgumentException().isThrownBy(() -> grant.grant(null));
        assertThatIllegalArgumentException().isThrownBy(() -> grant.grant(
                new ContentGrantRequest(" ", Format.PDF, Intent.STREAM, null, null, null, false)));
    }

    @Test
    void rejectsADevicePublicKeyThatIsNotValidRsaSpki() {
        assertThatThrownBy(() -> grant.grant(request(Format.PDF, false, new byte[]{1, 2, 3})))
                .hasMessageContaining("devicePublicKey");
    }

    // Explicit OAEPParameterSpec, not just the transformation string: the string's
    // "...AndMGF1Padding" suffix only names the OAEP digest, and the JCE silently defaults MGF1 to
    // SHA-1 without this spec — the exact bug this test exists to catch (B17). A cipher built the
    // same (under-specified) way ContentAccessGrantImpl used to build it would still "unwrap"
    // here, self-consistently, without ever proving interop with a real client — see git history
    // for the pre-fix version of this file, which did exactly that and passed anyway.
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private static byte[] unwrap(String wrappedBekBase64, PrivateKey devicePrivateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, devicePrivateKey, OAEP_SHA256);
        return cipher.doFinal(Base64.getDecoder().decode(wrappedBekBase64));
    }
}
