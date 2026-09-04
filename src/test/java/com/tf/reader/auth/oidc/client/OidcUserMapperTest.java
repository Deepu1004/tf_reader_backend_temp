package com.tf.reader.auth.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderUserDirectory;
import com.tf.reader.auth.repository.ReaderUserRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * OIDC claims to {@link TnfUser}, and the claims that are deliberately ignored.
 *
 * <p>The mirror of {@code SamlUserMapperTest}. No provider, no network and no Spring context: the
 * mapper's whole job is arithmetic on a claim set, and the claim set is the one thing a test can
 * construct exactly. What happens once an email is resolved - lookup versus provisioning, case
 * folding - is {@link com.tf.reader.auth.repository.ReaderUserDirectoryTest}'s subject, not this
 * one's; the fake repository here exists only to make {@code findOrProvisionIndividual} behave
 * like the real thing for a handful of known addresses.
 */
class OidcUserMapperTest {

	private final OidcUserMapper mapper = new OidcUserMapper(
			new ReaderUserDirectory(fakeRepository()), OidcProperties.forIssuer(ISSUER));

	private static final String ISSUER = "https://tnf.b2clogin.com/00000000-0000-0000-0000-000000000000/v2.0/";

	// ───────────────────────────── the happy path ─────────────────────────────

	@Test
	void anEmailsArrayResolvesToTheKnownIndividual() {
		// "emails" as a JSON array is what an Azure AD B2C user flow actually emits, and it is the
		// reason the mapper reads a claim that may be a list rather than calling getClaimAsString.
		TnfUser user = mapper.map(idToken(Map.of(
				"emails", List.of("john.doe@example.com"),
				"oid", "b2c-object-id")));

		assertThat(user.userId()).isEqualTo("usr_known_john");
		assertThat(user.type()).isEqualTo(UserType.INDIVIDUAL);
	}

	@Test
	void aPlainEmailClaimWorksToo() {
		// Entra External ID and the Microsoft identity platform emit a string, not an array.
		assertThat(mapper.map(idToken(Map.of("email", "john.doe@example.com"))).userId())
				.isEqualTo("usr_known_john");
	}

	@Test
	void theClaimsAreTriedInTheConfiguredOrder() {
		// emails, then email, then preferred_username, then upn. A token carrying several must
		// resolve by the most preferred one, or the mapping depends on map iteration order.
		TnfUser user = mapper.map(idToken(Map.of(
				"emails", List.of("jane.roe@example.com"),
				"email", "john.doe@example.com",
				"preferred_username", "someone.else@example.com")));

		assertThat(user.userId()).isEqualTo("usr_known_jane");
	}

	@Test
	void aLaterClaimIsUsedWhenTheEarlierOnesAreAbsentOrBlank() {
		// A user flow that has not been given the "emails" application claim emits it empty
		// rather than omitting it, so "present but useless" has to fall through like "absent".
		assertThat(mapper.map(idToken(Map.of(
				"emails", List.of(),
				"email", "   ",
				"preferred_username", "john.doe@example.com"))).userId())
				.isEqualTo("usr_known_john");
	}

	@Test
	void theClaimNamesAreConfigurable() {
		// A tenant emitting its email somewhere else is a configuration change, not a code change.
		OidcUserMapper custom = new OidcUserMapper(
				new ReaderUserDirectory(fakeRepository()),
				OidcProperties.withClaims(
						new OidcProperties.Claims(List.of("mail"), List.of("uid"))));

		assertThat(custom.map(idToken(Map.of("mail", "john.doe@example.com"))).userId())
				.isEqualTo("usr_known_john");
		assertThat(custom.resolveSubject(idToken(Map.of("uid", "u-1", "oid", "ignored"))))
				.isEqualTo("u-1");
	}

	@Test
	void theEmailIsFoldedByTheRepositoryNotByTheClaim() {
		// The provider is free to vary the case of an address it round-trips. The repository
		// lower-cases; this pins that a mixed-case claim still finds the known individual.
		assertThat(mapper.map(idToken(Map.of("email", "John.Doe@Example.COM"))).userId())
				.isEqualTo("usr_known_john");
	}

	// ───────────── an unknown identity is provisioned, not refused ─────────────

	@Test
	void anUnknownEmailIsProvisionedRatherThanRefused() {
		// Authenticated by the provider is enough here: unlike an institutional sign-in, there is
		// no roster to be a member of, so a first sign-in for a new address is a new account.
		TnfUser user = mapper.map(idToken(Map.of("email", "brand.new@example.com")));

		assertThat(user.userId()).startsWith("usr_");
		assertThat(user.type()).isEqualTo(UserType.INDIVIDUAL);
	}

	// ───────────── the claims that must NOT influence authorization ─────────────

	@Test
	void aRolesClaimCannotGrantAnApplicationRole() {
		// The headline security property of this class. Anybody able to edit the identity
		// provider's user flow output claims would otherwise be an ADMIN here.
		TnfUser user = mapper.map(idToken(Map.of(
				"email", "brand.new@example.com",
				"roles", List.of("ADMIN"),
				"role", "ADMIN",
				"groups", List.of("ADMIN"),
				"permissions", List.of("*"),
				"extension_roles", "ADMIN")));

		assertThat(user.roles()).containsExactly("SUBSCRIBER");
	}

	@Test
	void aTypeClaimCannotTurnAnIndividualIntoAnInstitutionalMember() {
		assertThat(mapper.map(idToken(Map.of(
				"email", "brand.new@example.com",
				"type", "INSTITUTION"))).type())
				.isEqualTo(UserType.INDIVIDUAL);
	}

	// ───────────────────────────── refusals ─────────────────────────────

	@Test
	void aTokenWithNoEmailClaimAtAllIsRefused() {
		// Refused rather than defaulted to the subject: a lookup by something that is not an email
		// address would either miss every time or, worse, one day hit.
		assertThatThrownBy(() -> mapper.map(idToken(Map.of("oid", "b2c-object-id", "name", "John"))))
				.isInstanceOf(ApiException.class)
				.extracting(thrown -> ((ApiException) thrown).code())
				.isEqualTo(ErrorCode.OIDC_AUTHENTICATION_FAILED);
	}

	@Test
	void anEmailClaimOfTheWrongTypeIsSkippedRatherThanCoerced() {
		// String.valueOf on a map or a number would produce a plausible-looking lookup key out of
		// nothing. Neither of these is a string, so neither is an email, so this refuses.
		assertThatThrownBy(() -> mapper.map(
				idToken(Map.of("emails", Map.of("value", "john.doe@example.com"), "email", 42))))
				.extracting(thrown -> ((ApiException) thrown).code())
				.isEqualTo(ErrorCode.OIDC_AUTHENTICATION_FAILED);
	}

	// ───────────────────────────── the subject ─────────────────────────────

	@Test
	void theSubjectPrefersOidOverSub() {
		// B2C's "sub" is pairwise - a different value per application - while "oid" is the
		// directory object id and is the same across every app in the tenant.
		assertThat(mapper.resolveSubject(idToken(Map.of("sub", "pairwise", "oid", "object-id"))))
				.isEqualTo("object-id");
		assertThat(mapper.resolveSubject(idToken(Map.of("sub", "pairwise"))))
				.isEqualTo("pairwise");
	}

	@Test
	void aTokenWithNoSubjectClaimStillSignsIn() {
		// The subject is evidence for the audit trail, not identity. Its absence is not a refusal.
		assertThat(mapper.resolveSubject(idToken(Map.of("email", "brand.new@example.com")))).isNull();
		assertThat(mapper.map(idToken(Map.of("email", "brand.new@example.com")))).isNotNull();
	}

	/**
	 * A repository stubbed for a couple of known addresses, plus real enough {@code save}
	 * behaviour that {@code findOrProvisionIndividual} can provision a genuinely new one. Backed
	 * by a map rather than Mockito's default answers, so a provisioned user is findable again -
	 * not exercised here, but what stops a fragile mock silently returning null mid-test.
	 */
	private static ReaderUserRepository fakeRepository() {
		ReaderUserRepository repository = mock(ReaderUserRepository.class);
		Map<String, ReaderUser> byEmail = new ConcurrentHashMap<>();
		byEmail.put("john.doe@example.com", individual("usr_known_john", "john.doe@example.com"));
		byEmail.put("jane.roe@example.com", individual("usr_known_jane", "jane.roe@example.com"));

		when(repository.findByEmailAndInstitutionIdIsNull(any()))
				.thenAnswer(invocation -> Optional.ofNullable(byEmail.get(invocation.getArgument(0))));
		when(repository.save(any())).thenAnswer(invocation -> {
			ReaderUser toSave = invocation.getArgument(0);
			byEmail.put(toSave.getEmail(), toSave);
			return toSave;
		});
		return repository;
	}

	private static ReaderUser individual(String userId, String email) {
		return ReaderUser.builder()
				.id(userId)
				.email(email)
				.type(UserType.INDIVIDUAL)
				.institutionId(null)
				.roles(List.of("SUBSCRIBER"))
				.collections(List.of())
				.build();
	}

	/**
	 * An ID token with the given claims. Never signed and never verified here - this class tests
	 * what the mapper does with a token Spring Security has <em>already</em> validated, and the
	 * validation itself is {@code OidcIdTokenValidationTest}'s subject.
	 */
	private static Jwt idToken(Map<String, Object> claims) {
		Jwt.Builder builder = Jwt.withTokenValue("id-token-value")
				.header("alg", "RS256")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300));
		claims.forEach(builder::claim);
		return builder.build();
	}
}
