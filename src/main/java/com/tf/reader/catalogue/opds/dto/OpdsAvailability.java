package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Present only on a {@code subscribe} link, for a book this caller cannot obtain - wokay
 * never calls flambeau while building a feed, so "available" is never something we can say. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsAvailability(String state) {

    public static final OpdsAvailability UNAVAILABLE = new OpdsAvailability("unavailable");
}
