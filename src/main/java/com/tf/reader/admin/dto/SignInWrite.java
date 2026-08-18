package com.tf.reader.admin.dto;

/** The sign-in details an operator can set on an institution. Method is always "SAML". */
public record SignInWrite(String method, String idpHint) {

    public static final int MAX_IDP_HINT_LENGTH = 60;
}
