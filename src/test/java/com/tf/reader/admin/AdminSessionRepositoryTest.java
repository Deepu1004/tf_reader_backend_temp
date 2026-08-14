package com.tf.reader.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DuplicateKeyException;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.repository.AdminSessionRepository;

/**
 * The guarded session updates, tested directly.
 *
 * <p>These are the operations that make refresh-token reuse detectable, so they are exercised at the
 * persistence layer rather than only through HTTP. Rows are addressed by refresh-token hash, because
 * an opaque token carries no session id.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AdminSessionRepositoryTest {

	@Autowired
	private AdminSessionRepository adminSessionRepository;

	@Autowired
	private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

	@BeforeEach
	void clearSessions() {
		this.adminSessionRepository.deleteAll();
	}

	@Test
	void claimsALiveRowByRevokingIt() {
		givenSession("sess_1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		Optional<AdminSession> claimed = this.adminSessionRepository.revokeForExchange("hash-1", "ROTATED",
				Instant.now());

		assertThat(claimed).isPresent();
		assertThat(claimed.get().getId()).isEqualTo("sess_1");
		// The returned row is the pre-update one, so it still carries the expiry the replacement inherits.
		assertThat(claimed.get().getExpiresAt()).isNotNull();

		AdminSession stored = this.adminSessionRepository.findById("sess_1").orElseThrow();
		assertThat(stored.getRevokedAt()).isNotNull();
		assertThat(stored.getRevokedReason()).isEqualTo("ROTATED");
	}

	@Test
	void refusesAHashItDoesNotHold() {
		givenSession("sess_1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		assertThat(this.adminSessionRepository.revokeForExchange("wrong-hash", "ROTATED", Instant.now()))
				.isEmpty();

		assertThat(this.adminSessionRepository.findById("sess_1").orElseThrow().getRevokedAt()).isNull();
	}

	/** An already-used row and an expired row are both unexchangeable. */
	@Test
	void refusesARevokedOrExpiredRow() {
		givenSession("revoked", "hash-revoked", Instant.now().plus(Duration.ofDays(1)));
		this.adminSessionRepository.revoke("revoked", "LOGOUT", Instant.now());

		givenSession("expired", "hash-expired", Instant.now().minus(Duration.ofMinutes(1)));

		assertThat(this.adminSessionRepository.revokeForExchange("hash-revoked", "ROTATED", Instant.now()))
				.isEmpty();
		assertThat(this.adminSessionRepository.revokeForExchange("hash-expired", "ROTATED", Instant.now()))
				.isEmpty();

		// The revocation reason of the already-revoked row is preserved.
		assertThat(this.adminSessionRepository.findById("revoked").orElseThrow().getRevokedReason())
				.isEqualTo("LOGOUT");
	}

	@Test
	void refusesAnUnknownHash() {
		assertThat(this.adminSessionRepository.revokeForExchange("nope", "ROTATED", Instant.now())).isEmpty();
	}

	/** One token can never belong to two rows, so a hash collision cannot widen access. */
	@Test
	void refusesTwoRowsWithTheSameRefreshTokenHash() {
		givenSession("sess_1", "shared-hash", Instant.now().plus(Duration.ofDays(1)));

		assertThatThrownBy(() -> givenSession("sess_2", "shared-hash", Instant.now().plus(Duration.ofDays(1))))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void findsARowByItsTokenHash() {
		givenSession("sess_1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		assertThat(this.adminSessionRepository.findByRefreshTokenHash("hash-1"))
				.get()
				.satisfies(session -> assertThat(session.getId()).isEqualTo("sess_1"));
		assertThat(this.adminSessionRepository.findByRefreshTokenHash("never-issued")).isEmpty();
	}

	/**
	 * The property the whole reuse-detection scheme rests on: if the same refresh token is presented
	 * twice concurrently, exactly one attempt may claim it.
	 */
	@Test
	void allowsOnlyOneOfManyConcurrentExchangesOfTheSameToken() throws Exception {
		givenSession("race", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		int attempts = 12;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		try {
			List<Callable<Boolean>> exchanges = java.util.stream.IntStream.range(0, attempts)
					.<Callable<Boolean>>mapToObj(i -> () -> this.adminSessionRepository
							.revokeForExchange("hash-1", "ROTATED", Instant.now())
							.isPresent())
					.toList();

			long winners = 0;
			for (Future<Boolean> outcome : executor.invokeAll(exchanges)) {
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
		givenSession("sess_1", "hash-1", Instant.now().plus(Duration.ofDays(1)));

		assertThat(this.adminSessionRepository.revoke("sess_1", "LOGOUT", Instant.now())).isTrue();
		assertThat(this.adminSessionRepository.revoke("sess_1", "SOMETHING_ELSE", Instant.now())).isFalse();
		assertThat(this.adminSessionRepository.revoke("unknown", "LOGOUT", Instant.now())).isFalse();

		AdminSession session = this.adminSessionRepository.findById("sess_1").orElseThrow();
		assertThat(session.getRevokedAt()).isNotNull();
		assertThat(session.getRevokedReason()).isEqualTo("LOGOUT");
	}

	@Test
	void reportsActiveOnlyForLiveSessions() {
		Instant now = Instant.now();
		givenSession("live", "hash-live", now.plus(Duration.ofDays(1)));
		givenSession("gone", "hash-gone", now.minus(Duration.ofDays(1)));
		givenSession("dead", "hash-dead", now.plus(Duration.ofDays(1)));
		this.adminSessionRepository.revoke("dead", "LOGOUT", now);

		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("live", now)).isTrue();
		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("gone", now)).isFalse();
		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("dead", now)).isFalse();
		assertThat(this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter("absent", now))
				.isFalse();
	}

	/**
	 * The contract calls both of these mandatory: the unique index because every refresh is a lookup by
	 * hash, and the TTL index because logout only marks a row revoked and would otherwise let this
	 * collection grow forever.
	 */
	@Test
	void createsTheTwoMandatoryIndexes() {
		// Touch the collection so Spring has certainly applied the entity's index definitions.
		givenSession("sess_index", "hash-index", Instant.now().plus(Duration.ofDays(1)));

		List<org.springframework.data.mongodb.core.index.IndexInfo> indexes = this.mongoTemplate
				.indexOps("adminSessions").getIndexInfo();

		assertThat(indexes)
				.anySatisfy(index -> {
					assertThat(index.isUnique()).isTrue();
					assertThat(index.getIndexFields()).singleElement()
							.satisfies(field -> assertThat(field.getKey()).isEqualTo("refreshTokenHash"));
				})
				.anySatisfy(index -> {
					assertThat(index.getExpireAfter()).isPresent();
					assertThat(index.getIndexFields()).singleElement()
							.satisfies(field -> assertThat(field.getKey()).isEqualTo("expiresAt"));
				});
	}

	private void givenSession(String id, String refreshTokenHash, Instant expiresAt) {
		AdminSession session = new AdminSession();
		session.setId(id);
		session.setAdminUserId("admin-1");
		session.setRefreshTokenHash(refreshTokenHash);
		session.setIssuedAt(Instant.now());
		session.setExpiresAt(expiresAt);
		this.adminSessionRepository.save(session);
	}

}
