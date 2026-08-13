package com.tf.reader.content.service;

import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.content.api.ContentAccessGrant;
import com.tf.reader.content.api.ContentGrant;
import com.tf.reader.content.api.ContentGrantRequest;
import com.tf.reader.content.api.Format;
import com.tf.reader.content.api.Intent;
import com.tf.reader.content.api.LoanProof;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ContentAccessGrantImplTest {

    private final ContentAccessGrant grant = new ContentAccessGrantImpl();

    private static ContentGrantRequest request(boolean wantSearchIndex) {
        return new ContentGrantRequest(
                "item_c25",
                Format.PDF,
                Intent.STREAM,
                new byte[]{1, 2, 3},
                new SubjectRef("u_88", "inst_7f3"),
                new LoanProof("loan_88", Instant.parse("2026-08-21T10:00:00Z")),
                wantSearchIndex
        );
    }

    @Test
    void returnsAContentLinkAndKeyMaterial() {
        ContentGrant result = grant.grant(request(true));

        assertThat(result.content().url()).contains("item_c25");
        assertThat(result.content().expiresAt()).isAfter(Instant.now());
        assertThat(result.content().mimeType()).isEqualTo("application/pdf");
        assertThat(result.content().cipherLength()).isPositive();
        assertThat(result.content().originalLength()).isPositive();

        assertThat(result.index()).isNotNull();
        assertThat(result.index().url()).contains("item_c25");

        assertThat(result.encryption().algorithm()).isEqualTo("AES-256-GCM");
        assertThat(result.encryption().wrapAlgorithm()).isEqualTo("RSA-OAEP-256");
        assertThat(result.encryption().wrappedBek()).isNotBlank();
    }

    @Test
    void omitsTheIndexWhenItWasNotAskedFor() {
        assertThat(grant.grant(request(false)).index()).isNull();
    }

    @Test
    void rejectsAMalformedRequest() {
        assertThatIllegalArgumentException().isThrownBy(() -> grant.grant(null));
        assertThatIllegalArgumentException().isThrownBy(() -> grant.grant(
                new ContentGrantRequest(" ", Format.PDF, Intent.STREAM, null, null, null, false)));
    }
}
