package com.tf.reader.content.api;

public record IndexUrl(
        String url,
        boolean encrypted,
        Integer termCount
) {
}
