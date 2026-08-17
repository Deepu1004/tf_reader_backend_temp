package com.tf.reader.common.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class AdminAuditWriter {


	private static final Set<String> SENSITIVE_KEYS =
			Set.of("passwordhash", "password", "token", "accesstoken", "refreshtoken");

	private final AuditLogRepository auditLogRepository;

	public void record(AuditLog.Action action, String entityType, String entityId,
			Map<String, Object> before, Map<String, Object> after) {
		AuditLog log = new AuditLog();
		log.setActorId(currentAdminId());
		log.setActorEmail(null);
		log.setAction(action);
		log.setEntityType(entityType);
		log.setEntityId(entityId);
		log.setBefore(strip(before));
		log.setAfter(strip(after));
		log.setAt(Instant.now());

		auditLogRepository.save(log);
	}

	private static Map<String, Object> strip(Map<String, Object> fields) {
		if (fields == null) {
			return null;
		}
		Map<String, Object> copy = new LinkedHashMap<>(fields);
		copy.keySet().removeIf(key -> SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT)));
		return copy;
	}

	private static String currentAdminId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		return (authentication.getPrincipal() instanceof Jwt jwt) ? jwt.getSubject() : null;
	}

}
