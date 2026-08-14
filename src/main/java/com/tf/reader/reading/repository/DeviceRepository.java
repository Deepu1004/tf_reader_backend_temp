package com.tf.reader.reading.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.reading.entity.DeviceFingerprint;

/**
 * The {@code devices} collection.
 *
 * <p><b>Never injected outside {@code reading/}.</b> If another capability ever needs to know
 * something about a reader's devices, that is a published contract in {@code reading/api}, not this
 * interface — a repository crossing a boundary is how two capabilities end up owning one collection.
 *
 * <p>Deliberately thin: the device cap does not use it. The cap needs a conditional update whose
 * filter <em>is</em> the check, which is a {@code MongoTemplate} operation rather than a derived
 * query. This interface is for the ordinary reads — a support screen, a test assertion.
 */
public interface DeviceRepository extends MongoRepository<DeviceFingerprint, String> {

	Optional<DeviceFingerprint> findByUserId(String userId);
}
