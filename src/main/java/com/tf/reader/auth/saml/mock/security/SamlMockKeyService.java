package com.tf.reader.auth.saml.mock.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.credential.Credential;
import org.springframework.util.Assert;

import com.tf.reader.auth.saml.mock.config.SamlMockComponent;
import com.tf.reader.auth.saml.mock.config.SamlMockProperties;

/**
 * The mock IdP's signing key.
 *
 * <p><b>Loaded, not generated.</b> The OIDC mock generates a fresh RSA key every run because its
 * client fetches the JWKS lazily, on first token validation - long after startup. SAML's relying
 * party resolves its trusted certificate once, eagerly, during application context startup,
 * before this application's own embedded server is accepting connections; a certificate fetched
 * from "this same server, later" would deadlock. So this key pair is fixed instead: generated
 * once, its public half committed as {@code saml-mock-idp-local.crt}, its private half read from
 * {@code saml-mock.private-key} - which lives only in {@code application-local.yml}, gitignored,
 * exactly where the OIDC mock's own dev-only secret already lives.
 *
 * <p>The public key is derived from the private key's own CRT parameters rather than read from a
 * second location, so there is exactly one place this key pair is configured from.
 */
@SamlMockComponent
public class SamlMockKeyService {

	private final Credential credential;

	public SamlMockKeyService(SamlMockProperties properties) {
		Assert.hasText(properties.privateKey(),
				"saml-mock.private-key must be set - see application-local.yml");
		PrivateKey privateKey = readPrivateKey(properties.privateKey());
		PublicKey publicKey = publicKeyOf(privateKey);
		this.credential = new BasicCredential(publicKey, privateKey);
	}

	/** The credential {@code SamlMockResponseBuilder} signs assertions with. */
	public Credential credential() {
		return credential;
	}

	private static PrivateKey readPrivateKey(String pem) {
		String base64 = pem
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
			return KeyFactory.getInstance("RSA").generatePrivate(spec);
		}
		catch (GeneralSecurityException | IllegalArgumentException failure) {
			throw new IllegalStateException(
					"saml-mock.private-key is not a valid PKCS8 RSA private key", failure);
		}
	}

	/** RSA private keys carry their own public exponent and modulus; no second key to configure. */
	private static PublicKey publicKeyOf(PrivateKey privateKey) {
		if (!(privateKey instanceof RSAPrivateCrtKey rsaKey)) {
			throw new IllegalStateException("saml-mock.private-key must be an RSA key");
		}
		try {
			RSAPublicKeySpec spec =
					new RSAPublicKeySpec(rsaKey.getModulus(), rsaKey.getPublicExponent());
			return KeyFactory.getInstance("RSA").generatePublic(spec);
		}
		catch (GeneralSecurityException failure) {
			throw new IllegalStateException("could not derive the mock IdP's public key", failure);
		}
	}
}
