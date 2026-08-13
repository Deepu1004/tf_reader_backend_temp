package com.tf.reader.admin.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.common.security.TokenAudience;
import com.tf.reader.common.security.TokenClaims;

/**
 * Mints arbitrary tokens for tests, including ones the application would never issue.
 *
 * <p>Lets a test construct a token that is correctly signed but wrong in exactly one respect
 * (audience, issuer, intent, expiry) so each validation rule can be exercised on its own.
 */
final class TestTokenFactory {

	/** A well-formed key that is simply not the application's key. */
	private static final String FOREIGN_SECRET = "a-completely-different-key-used-only-by-tests-98765";

	/**
	 * The audience refresh tokens used to carry before they became opaque. Nothing issues it now, so a
	 * JWT claiming it must authenticate nothing at all.
	 */
	static final String RETIRED_REFRESH_AUDIENCE = "tf-refresh";

	/** The {@code token_use} value that went with it. */
	static final String RETIRED_REFRESH_TOKEN_USE = "refresh";

	private final JwtEncoder applicationEncoder;
	private final JwtEncoder foreignEncoder;
	private final String issuer;

	TestTokenFactory(JwtEncoder applicationEncoder, String issuer) {
		this.applicationEncoder = applicationEncoder;
		this.issuer = issuer;
		this.foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
				new SecretKeySpec(FOREIGN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
	}

	/** A valid admin access token: correct issuer, audience, intent, role and session. */
	JwtClaimsSet.Builder adminAccessClaims(String adminUserId, String sessionId, AdminRole role) {
		return baseClaims(adminUserId, TokenAudience.ADMIN, TokenClaims.USE_ACCESS)
				.claim(TokenClaims.SESSION_ID, sessionId)
				.claim(TokenClaims.ROLE, role.name());
	}

	/** An app access token, which the admin surface must never accept. */
	JwtClaimsSet.Builder appAccessClaims(String subject) {
		return baseClaims(subject, TokenAudience.APP, TokenClaims.USE_ACCESS);
	}

	/** A JWT shaped like the refresh tokens this service no longer issues. */
	JwtClaimsSet.Builder retiredRefreshClaims(String adminUserId, String sessionId) {
		return baseClaims(adminUserId, RETIRED_REFRESH_AUDIENCE, RETIRED_REFRESH_TOKEN_USE)
				.claim(TokenClaims.SESSION_ID, sessionId);
	}

	private JwtClaimsSet.Builder baseClaims(String subject, String audience, String tokenUse) {
		Instant now = Instant.now();
		return JwtClaimsSet.builder()
				.issuer(this.issuer)
				.subject(subject)
				.audience(List.of(audience))
				.issuedAt(now)
				.expiresAt(now.plus(15, ChronoUnit.MINUTES))
				.id(UUID.randomUUID().toString())
				.claim(TokenClaims.TOKEN_USE, tokenUse);
	}

	/** Signs with the application's key, so only the claims can make the token invalid. */
	String sign(JwtClaimsSet claims) {
		return encode(this.applicationEncoder, claims);
	}

	/** Signs with a different key, producing a structurally valid token with a bad signature. */
	String signWithForeignKey(JwtClaimsSet claims) {
		return encode(this.foreignEncoder, claims);
	}

	private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	/** Well past the decoder's default 60 second clock skew allowance. */
	static Instant wellInThePast() {
		return Instant.now().minus(1, ChronoUnit.HOURS);
	}

}
