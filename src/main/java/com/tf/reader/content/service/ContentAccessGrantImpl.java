package com.tf.reader.content.service;

import com.tf.reader.content.api.ContentAccessGrant;
import com.tf.reader.content.api.ContentGrant;
import com.tf.reader.content.api.ContentGrantRequest;
import com.tf.reader.content.api.Encryption;
import com.tf.reader.content.api.Format;
import com.tf.reader.content.api.IndexUrl;
import com.tf.reader.content.api.SignedUrl;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;


@Service
class ContentAccessGrantImpl implements ContentAccessGrant {

    private static final Duration PLACEHOLDER_URL_TTL = Duration.ofMinutes(15);
    private static final String PLACEHOLDER_BASE_URL = "http://localhost:9000/placeholder";
    private static final long PLACEHOLDER_ORIGINAL_LENGTH = 2_170_646L;
    private static final long PLACEHOLDER_CIPHER_LENGTH = PLACEHOLDER_ORIGINAL_LENGTH + 28L;
    private static final int PLACEHOLDER_TERM_COUNT = 6_120;

   
    private static final String PLACEHOLDER_WRAPPED_BEK = Base64.getEncoder()
            .encodeToString("placeholder-wrapped-bek".getBytes(StandardCharsets.UTF_8));

    @Override
    public ContentGrant grant(ContentGrantRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.itemId() == null || request.itemId().isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }

        Instant expiresAt = Instant.now().plus(PLACEHOLDER_URL_TTL);
        String itemId = request.itemId();

        SignedUrl content = new SignedUrl(
                PLACEHOLDER_BASE_URL + "/" + itemId + "/content.enc",
                expiresAt,
                PLACEHOLDER_CIPHER_LENGTH,
                PLACEHOLDER_ORIGINAL_LENGTH,
                mimeTypeFor(request.format())
        );

        IndexUrl index = request.wantSearchIndex()
                ? new IndexUrl(PLACEHOLDER_BASE_URL + "/" + itemId + "/index.enc", true, PLACEHOLDER_TERM_COUNT)
                : null;

        Encryption encryption = new Encryption(
                "AES-256-GCM",
                "nonce(12) || ciphertext || tag(16)",
                PLACEHOLDER_WRAPPED_BEK,
                "RSA-OAEP-256",
                "placeholder",
                "sha256:placeholder"
        );

        return new ContentGrant(content, index, encryption);
    }

    private static String mimeTypeFor(Format format) {
        if (format == null) {
            return "application/octet-stream";
        }
        return switch (format) {
            case PDF -> "application/pdf";
            case EPUB -> "application/epub+zip";
            case AUDIO -> "audio/mpeg";
        };
    }
}
