package com.tf.reader.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.repository.AdminSessionRepository;

/**
 * The guarded session updates, tested directly.
 *
 * <p>These are the operations that make refresh-token reuse detectable, so they are exercised at the
 * persistence layer rather than only through HTTP.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AdminSessionRepositoryTest {

	@Autowired
	private AdminSessionRepository adminSessionRepository;

	@BeforeEach
	void clearSessions() {
		this.adminSessionRepository.deleteAll();
	}

	@Test
	void rotatesWhenThePresentedTokenIsTheCurrentOne() {
		givenSession("session-1", "jti-1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		Optional<AdminSession> rotated = this.adminSessionRepository.rotateRefreshToken("session-1", "jti-1",
				"hash-1", "jti-2", "hash-2", Instant.now());

		assertThat(rotated).isPresent();
		assertThat(rotated.get().getCurrentRefreshJti()).isEqualTo("jti-2");
		assertThat(rotated.get().getCurrentRefreshTokenHash()).isEqualTo("hash-2");
		assertThat(rotated.get().getLastRotatedAt()).isNotNull();
	}

	@Test
	void refusesToRotateOnAStaleJtiOrMismatchedHash() {
		givenSession("session-1", "jti-1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		assertThat(this.adminSessionRepository.rotateRefreshToken("session-1", "jti-0", "hash-1", "jti-2",
				"hash-2", Instant.now())).isEmpty();
		assertThat(this.adminSessionRepository.rotateRefreshToken("session-1", "jti-1", "wrong-hash", "jti-2",
				"hash-2", Instant.now())).isEmpty();

		// Nothing changed after either refusal.
		assertThat(this.adminSessionRepository.findById("session-1").orElseThrow().getCurrentRefreshJti())
				.isEqualTo("jti-1");
	}

	@Test
	void refusesToRotateARevokedOrExpiredSession() {
		givenSession("revoked", "jti-1", "hash-1", Instant.now().plus(Duration.ofDays(1)));
		this.adminSessionRepository.revoke("revoked", "LOGOUT", Instant.now());

		givenSession("expired", "jti-1", "hash-1", Instant.now().minus(Duration.ofMinutes(1)));

		assertThat(this.adminSessionRepository.rotateRefreshToken("revoked", "jti-1", "hash-1", "jti-2", "hash-2",
				Instant.now())).isEmpty();
		assertThat(this.adminSessionRepository.rotateRefreshToken("expired", "jti-1", "hash-1", "jti-2", "hash-2",
				Instant.now())).isEmpty();
	}

	@Test
	void refusesToRotateAnUnknownSession() {
		assertThat(this.adminSessionRepository.rotateRefreshToken("nope", "jti-1", "hash-1", "jti-2", "hash-2",
				Instant.now())).isEmpty();
	}

	/**
	 * The property the whole reuse-detection scheme rests on: if the same refresh token is presented
	 * twice concurrently, exactly one attempt may win.
	 */
	@Test
	void allowsOnlyOneOfManyConcurrentRotationsOfTheSameToken() throws Exception {
		givenSession("race", "jti-1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		int attempts = 12;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		try {
			List<Callable<Boolean>> rotations = java.util.stream.IntStream.range(0, attempts)
					.<Callable<Boolean>>mapToObj(i -> () -> this.adminSessionRepository
							.rotateRefreshToken("race", "jti-1", "hash-1", "jti-new-" + i, "hash-new-" + i,
									Instant.now())
							.isPresent())
					.toList();

			long winners = 0;
			for (Future<Boolean> outcome : executor.invokeAll(rotations)) {
				if (outcome.get()) {
					winners++;
				}
			}

			assertThat(winners).isEqualTo(1);
		}
		finally {
			executor.shutdown();
			assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
	}

	@Test
	void revokesOnceAndReportsSubsequentCallsAsNoOps() {
		givenSession("session-1", "jti-1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		assertThat(this.adminSessionRepository.revoke("session-1", "LOGOUT", Instant.now())).isTrue();
		assertThat(this.adminSessionRepository.revoke("session-1", "SOMETHING_ELSE", Instant.now())).isFalse();
		assertThat(this.adminSessionRepository.revoke("unknown", "LOGOUT", Instant.now())).isFalse();

		AdminSession session = this.adminSessionRepository.findById("session-1").orElseThrow();
		assertThat(session.getRevokedAt()).isNotNull();
		assertThat(session.getRevokedReason()).isEqualTo("LOGOUT");
	}

	@Test
	void reportsActiveOnlyForLiveSessions() {
		Instant now = Instant.now();
		givenSession("live", "jti-1", "hash-1", now.plus(Duration.ofDays(1)));
		givenSession("gone", "jti-1", "hash-1", now.minus(Duration.ofDays(1)));
		givenSession("dead", "jti-1", "hash-1", now.plus(Duration.ofDays(1)));
		this.adminSessionRepository.revoke("dead", "LOGOUT", now);

		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("live", now)).isTrue();
		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("gone", now)).isFalse();
		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("dead", now)).isFalse();
		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("absent", now))
				.isFalse();
	}

	private void givenSession(String id, String jti, String hash, Instant expiresAt) {
		AdminSession session = new AdminSession();
		session.setId(id);
		session.setAdminUserId("admin-1");
		session.setCurrentRefreshJti(jti);
		session.setCurrentRefreshTokenHash(hash);
		session.setIssuedAt(Instant.now());
		session.setLastRotatedAt(Instant.now());
		session.setExpiresAt(expiresAt);
		this.adminSessionRepository.save(session);
	}

}
