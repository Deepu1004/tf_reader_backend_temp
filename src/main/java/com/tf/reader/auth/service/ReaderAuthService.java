package com.tf.reader.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tf.reader.auth.dto.TokenResponse;
import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.Role;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderUserRepository;
import com.tf.reader.auth.service.ReaderSessionService.IssuedRefreshToken;
import com.tf.reader.auth.token.IssuedToken;
import com.tf.reader.auth.token.TokenService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * B2C email-and-password sign up and sign in: the credential store of our own that a reader who
 * is not a member of any institution uses, rather than going through their institution's SAML or
 * OIDC identity provider.
 *
 * <p>Mirrors {@code AdminAuthService}'s password check - BCrypt, with a timing-equalisation hash
 * so an unknown email costs the same as a known one - but issues the app's own {@link TnfUser}
 * token pair instead of an admin session, the same pair {@code AuthController.refresh} issues.
 */
@Service
public class ReaderAuthService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ReaderUserRepository readerUsers;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final ReaderSessionService readerSessions;

	/** Verified against when no reader matches, so an unknown email costs the same as a known one. */
	private final String timingEqualisationHash;

	public ReaderAuthService(ReaderUserRepository readerUsers, PasswordEncoder passwordEncoder,
			TokenService tokenService, ReaderSessionService readerSessions) {
		this.readerUsers = readerUsers;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.readerSessions = readerSessions;
		this.timingEqualisationHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Provisions a new individual reader with a hashed password and signs them straight in.
	 *
	 * @throws ApiException {@code EMAIL_TAKEN} if an individual reader already holds this email
	 */
	public TokenResponse signUp(String email, String rawPassword) {
		String folded = fold(email);

		ReaderUser reader = ReaderUser.builder()
				.id(newIndividualUserId())
				.email(folded)
				.type(UserType.INDIVIDUAL)
				.institutionId(null)
				.roles(List.of(Role.SUBSCRIBER.name()))
				.collections(List.of())
				.passwordHash(passwordEncoder.encode(rawPassword))
				.build();

		try {
			reader = readerUsers.save(reader);
		}
		catch (DuplicateKeyException alreadyRegistered) {
			throw new ApiException(ErrorCode.EMAIL_TAKEN,
					"An individual reader is already registered with this email.");
		}

		return issueTokens(reader.toTnfUser());
	}

	/**
	 * Verifies an individual reader's password and signs them in. Unknown email and wrong
	 * password are indistinguishable: same exception, comparable timing either way.
	 */
	public TokenResponse login(String email, String rawPassword) {
		String folded = fold(email);
		Optional<ReaderUser> candidate = readerUsers.findByEmailAndInstitutionIdIsNull(folded);

		String storedHash = candidate.map(ReaderUser::getPasswordHash)
				.filter(hash -> hash != null && !hash.isBlank())
				.orElse(timingEqualisationHash);

		boolean passwordMatches = passwordEncoder.matches(rawPassword, storedHash);

		if (candidate.isEmpty() || !passwordMatches) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED, "Email or password is not valid.");
		}

		return issueTokens(candidate.get().toTnfUser());
	}

	private TokenResponse issueTokens(TnfUser user) {
		IssuedToken accessToken = tokenService.issue(user);
		IssuedRefreshToken refreshToken = readerSessions.createSession(user);

		return new TokenResponse(accessToken.token(), refreshToken.value(),
				Duration.between(accessToken.issuedAt(), accessToken.expiresAt()).getSeconds());
	}

	/** {@link java.util.Locale#ROOT}-folded, matching {@code ReaderUserDirectory}. */
	private static String fold(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String newIndividualUserId() {
		byte[] bytes = new byte[8];
		RANDOM.nextBytes(bytes);
		return "usr_" + HexFormat.of().formatHex(bytes);
	}
}
