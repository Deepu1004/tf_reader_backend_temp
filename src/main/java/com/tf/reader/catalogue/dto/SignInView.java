package com.tf.reader.catalogue.dto;

/**
 * Wire shape of an institution's sign-in block. Detail endpoint only: the list is what a user scans
 * before choosing, and nothing on it needs to know how sign-in works.
 */
public record SignInView(String method, String idpHint) {}