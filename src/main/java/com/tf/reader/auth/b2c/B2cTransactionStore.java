package com.tf.reader.auth.b2c;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Server-side store of in-flight B2C sign-ins, keyed by the {@code state} parameter.
 *
 * <p>The individual counterpart of
 * {@link com.tf.reader.auth.oidc.client.OidcTransactionStore} - same reasoning for state, nonce,
 * single use and in-memory storage - minus the institution, because this flow has none to carry.
 */
@Component
public class B2cTransactionStore {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	/** Above this many in-flight sign-ins, opening one first sweeps the expired ones. */
	static final int EVICT_ABOVE = 256;

	private final Map<String, B2cTransaction> byState = new ConcurrentHashMap<>();
	private final Clock clock;
	private final B2cProperties properties;

	public B2cTransactionStore(Clock clock, B2cProperties properties) {
		this.clock = clock;
		this.properties = properties;
	}

	/** Opens a transaction, with a fresh state and nonce. No institution to open it for. */
	public B2cTransaction open() {
		if (byState.size() >= EVICT_ABOVE) {
			evictExpired();
		}
		Instant now = clock.instant();
		B2cTransaction transaction = new B2cTransaction(
				randomValue("b2cTxn_"),
				// 24 bytes of SecureRandom each. State and nonce must be unguessable for their
				// checks to mean anything: a predictable state is a state an attacker can pre-empt.
				randomValue(""),
				randomValue(""),
				now,
				now.plus(properties.transactionTtl()));

		byState.put(transaction.state(), transaction);
		return transaction;
	}

	/**
	 * Consumes the transaction a callback refers to, removing it so it cannot be used twice.
	 *
	 * @return the transaction, or empty if the state is unknown, already used or expired
	 */
	public Optional<B2cTransaction> consume(String state) {
		if (state == null || state.isBlank()) {
			return Optional.empty();
		}
		B2cTransaction transaction = byState.remove(state);
		if (transaction == null || transaction.hasExpiredAt(clock.instant())) {
			return Optional.empty();
		}
		return Optional.of(transaction);
	}

	/** In-flight sign-ins currently held. Package-private: for tests, not for callers. */
	int inFlight() {
		return byState.size();
	}

	/** Drops transactions nobody came back for, so an idle process does not accumulate them. */
	public int evictExpired() {
		Instant now = clock.instant();
		int before = byState.size();
		byState.values().removeIf(transaction -> transaction.hasExpiredAt(now));
		return before - byState.size();
	}

	private static String randomValue(String prefix) {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return prefix + ENCODER.encodeToString(bytes);
	}
}
