package com.tf.reader.content.api;

public record ContentGrant(
        SignedUrl content,
        IndexUrl index,
        Encryption encryption
) {
}
