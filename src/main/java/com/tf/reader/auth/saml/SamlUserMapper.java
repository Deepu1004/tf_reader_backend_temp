package com.tf.reader.auth.saml;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tf.reader.auth.model.Role;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderSessionRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Turns a completed institutional sign-in into a TnF user, without ever asking who signed in.
 *
 * <p>A validly signed assertion is proof of institutional membership and nothing more - there is
 * no per-student account to resolve it against, no username, no email, no id anyone chose. The
 * institution comes from the sign-in transaction our own backend opened, never from the assertion
 * and never from the client. Identity is the device: a device signing in for the first time is
 * minted an opaque {@code deviceId}, and a returning device presents the one it already has to
 * reclaim the same loans and shelf, rather than starting over as somebody new.
 *
 * <p>Minting a new device is also the one place concurrency is enforced: an institution has a
 * fixed number of concurrent seats, spent by live sessions and freed as they end. A returning
 * device is never refused on this account - refusing one already in use, just because the cap
 * has since filled up, would read as a bug rather than a policy.
 */
@Component
public class SamlUserMapper {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ReaderSessionRepository readerSessions;
	private final Clock clock;
	private final int maxConcurrentSessionsPerInstitution;

	public SamlUserMapper(ReaderSessionRepository readerSessions, Clock clock,
			@Value("${tnf.auth.max-concurrent-sessions-per-institution:50}") int maxConcurrentSessionsPerInstitution) {
		this.readerSessions = readerSessions;
		this.clock = clock;
		this.maxConcurrentSessionsPerInstitution = maxConcurrentSessionsPerInstitution;
	}

	/**
	 * @param institutionId    the institution recovered from the sign-in transaction
	 * @param existingDeviceId the device id this device already holds from a previous sign-in to
	 *                         this institution, or null/blank for a device signing in for the
	 *                         first time
	 * @throws ApiException 403 ({@code SEAT_LIMIT_REACHED}) if this is a new device and the
	 *                       institution has no concurrent seat free
	 */
	public TnfUser map(String institutionId, String existingDeviceId) {
		String deviceId = existingDeviceId != null && !existingDeviceId.isBlank()
				? existingDeviceId
				: admitNewDevice(institutionId);
		return new TnfUser(deviceId, UserType.INSTITUTION, institutionId, List.of(Role.MEMBER.name()), List.of());
	}

	private String admitNewDevice(String institutionId) {
		long liveSessions = readerSessions.countByInstitutionIdAndRevokedAtIsNullAndExpiresAtAfter(
				institutionId, clock.instant());
		if (liveSessions >= maxConcurrentSessionsPerInstitution) {
			throw new ApiException(ErrorCode.SEAT_LIMIT_REACHED,
					"Institution '" + institutionId + "' has no concurrent seats free right now.");
		}
		return newDeviceId();
	}

	private static String newDeviceId() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return "dev_" + HexFormat.of().formatHex(bytes);
	}
}
