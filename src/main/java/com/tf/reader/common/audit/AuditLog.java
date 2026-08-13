package com.tf.reader.common.audit;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "auditLogs")
@CompoundIndexes({
		@CompoundIndex(name = "entity_at", def = "{'entityType': 1, 'entityId': 1, 'at': -1}"),
		@CompoundIndex(name = "actor_at", def = "{'actorId': 1, 'at': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

	@Id
	private String id;

	private String actorId;
	private String actorEmail;
	private Action action;
	private String entityType;
	private String entityId;

	// Only the changed fields, not the whole document either side.
	private Map<String, Object> before;
	private Map<String, Object> after;
	private Map<String, Object> meta;

	@Indexed(name = "at_ttl", expireAfter = "90d")
	private Instant at;

	public enum Action {
		CREATE,
		UPDATE,
		STATUS,
		LOGIN,
		INGEST,
		CONTENT_ACCESS
	}

}
