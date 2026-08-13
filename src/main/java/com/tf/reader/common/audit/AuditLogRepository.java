package com.tf.reader.common.audit;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

	List<AuditLog> findByEntityTypeAndEntityIdOrderByAtDesc(String entityType, String entityId);

	List<AuditLog> findByActorIdOrderByAtDesc(String actorId);

}
