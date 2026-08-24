package com.tf.reader.reading.service;

import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed implementation of {@link CopyLease}.
 *
 * <p>One sorted set per (scope, itemId): {@code lease:{scope}:{itemId}}, member an opaque lease
 * token, score the lease's expiry instant. Counting and reassigning both key off that score,
 * never a separate "who holds it" map — one row per copy holder. Tokens are self-describing
 * ({@code scope|itemId|random}) because {@link #release(String)} carries neither scope nor
 * itemId, and the token is the only place left to keep them.
 */
@Service
public class CopyLeaseImpl implements CopyLease {

	private static final Duration CLAIM_TTL = Duration.ofSeconds(30);

	// Check-then-add in one round trip — two separate calls (ZCOUNT then
	// ZADD) would let two concurrent claims both pass the check and both
	// add, a real double-booking bug, not a theoretical one.
	private static final DefaultRedisScript<Long> CLAIM = new DefaultRedisScript<>("""
			local key = KEYS[1]
			local now = tonumber(ARGV[1])
			local total = tonumber(ARGV[2])
			local member = ARGV[3]
			local expiresAt = ARGV[4]
			local leased = redis.call('ZCOUNT', key, now, '+inf')
			if leased >= total then
			    return 0
			end
			redis.call('ZADD', key, expiresAt, member)
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;
	private final Clock clock;

	public CopyLeaseImpl(StringRedisTemplate redis, Clock clock) {
		this.redis = redis;
		this.clock = clock;
	}

	@Override
	public Optional<LeaseHandle> claim(String scope, String itemId, int copies) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(CLAIM_TTL);
		String token = token(scope, itemId);
		Long result = redis.execute(CLAIM, List.of(key(scope, itemId)),
				String.valueOf(now.toEpochMilli()), String.valueOf(copies), token,
				String.valueOf(expiresAt.toEpochMilli()));
		if (result == null || result != 1L) {
			return Optional.empty();
		}
		return Optional.of(new LeaseHandle(token, scope, itemId, expiresAt));
	}

	@Override
	public Optional<LeaseHandle> acquire(String itemId) {
		// No scope on this call, and nothing in the codebase calls it today
		// (the copy-limited path uses claim, which has one). Raise with
		// Deepak before relying on it — a scope-less key would silently
		// pool every institution's copies of this item together.
		throw new UnsupportedOperationException("acquire(itemId) carries no scope — not implemented");
	}

	@Override
	public boolean extend(LeaseHandle handle, Instant until) {
		if (handle == null) {
			return false;
		}
		redis.opsForZSet().add(key(handle.scope(), handle.itemId()), handle.token(), until.toEpochMilli());
		return true;
	}

	@Override
	public void release(LeaseHandle handle) {
		if (handle != null) {
			redis.opsForZSet().remove(key(handle.scope(), handle.itemId()), handle.token());
		}
	}

	@Override
	public void release(String leaseId) {
		String[] parts = leaseId.split("\\|", 3);
		if (parts.length == 3) {
			redis.opsForZSet().remove(key(parts[0], parts[1]), leaseId);
		}
	}

	@Override
	public void reassign(String scope, String itemId, String fromToken, String newToken, Instant until) {
		// Add the new holder BEFORE removing the old one — for the instant
		// both rows exist, the copy still reads as leased twice over, never
		// as free. The order is the whole point.
		String key = key(scope, itemId);
		redis.opsForZSet().add(key, newToken, until.toEpochMilli());
		redis.opsForZSet().remove(key, fromToken);
	}

	@Override
	public int available(String scope, String itemId, int copies) {
		Long leased = redis.opsForZSet().count(key(scope, itemId), clock.instant().toEpochMilli(),
				Double.POSITIVE_INFINITY);
		return Math.max(0, copies - (leased == null ? 0 : leased.intValue()));
	}

	private static String key(String scope, String itemId) {
		return "lease:" + scope + ":" + itemId;
	}

	private static String token(String scope, String itemId) {
		return scope + "|" + itemId + "|" + UUID.randomUUID();
	}
}
