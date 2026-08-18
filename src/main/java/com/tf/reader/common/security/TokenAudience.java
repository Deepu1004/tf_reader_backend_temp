package com.tf.reader.common.security;

/** The two token audiences, and there is no third. Refresh tokens are opaque and carry no audience. */
public final class TokenAudience {

	public static final String ADMIN = "tf-admin";

	public static final String APP = "tf-app";

	private TokenAudience() {
	}

}
