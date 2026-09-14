package com.tf.reader.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.common.security.JwtProperties;
import com.tf.reader.common.security.TokenClaims;

class AdminTokenServiceTest {

	private static final SecretKey SIGNING_KEY = new SecretKeySpec(
			"a-test-only-signing-secret-of-sufficient-length-0123456789".getBytes(), "HmacSHA256");

	private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(SIGNING_KEY));
	private final JwtProperties jwtProperties = new JwtProperties("tf-reader", null, null, null);
	private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC);

	private final AdminTokenService service = new AdminTokenService(this.jwtEncoder, this.jwtProperties, this.clock);

	@Test
	void mintsTenantIdClaimWhenAdminHasPublisherId() throws ParseException {
		AdminUser admin = publisherScopedAdmin("pub_rtlg");

		AdminTokenService.MintedToken token = this.service.mintAccessToken(admin, "sess_1");

		assertThat(claimsOf(token).getStringClaim(TokenClaims.TENANT_ID)).isEqualTo("pub_rtlg");
	}

	@Test
	void omitsTenantIdClaimWhenAdminHasNoPublisherId() throws ParseException {
		AdminUser admin = superAdmin();

		AdminTokenService.MintedToken token = this.service.mintAccessToken(admin, "sess_2");

		assertThat(claimsOf(token).getClaims()).doesNotContainKey(TokenClaims.TENANT_ID);
	}

	/** Reads the claims straight off the signature, since the fixed test clock is far in the past
	 * relative to real time and a normal {@code JwtDecoder} would reject the token as expired. */
	private com.nimbusds.jwt.JWTClaimsSet claimsOf(AdminTokenService.MintedToken token) throws ParseException {
		try {
			SignedJWT jwt = SignedJWT.parse(token.value());
			if (!jwt.verify(new MACVerifier(SIGNING_KEY))) {
				throw new IllegalStateException("token signature did not verify");
			}
			return jwt.getJWTClaimsSet();
		} catch (com.nimbusds.jose.JOSEException e) {
			throw new IllegalStateException(e);
		}
	}

	private static AdminUser publisherScopedAdmin(String publisherId) {
		AdminUser admin = new AdminUser();
		admin.setId("adm_pub");
		admin.setEmail("publisher-admin@tandf.example");
		admin.setRole(AdminRole.PUBLISHER_ADMIN);
		admin.setPublisherId(publisherId);
		admin.setStatus(AdminStatus.ACTIVE);
		return admin;
	}

	private static AdminUser superAdmin() {
		AdminUser admin = new AdminUser();
		admin.setId("adm_super");
		admin.setEmail("super-admin@tandf.example");
		admin.setRole(AdminRole.SUPER_ADMIN);
		admin.setStatus(AdminStatus.ACTIVE);
		return admin;
	}

}
