package com.tf.reader.sync.service;

import com.tf.reader.sync.dto.PersonalizationRequest;
import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Personalization;
import com.tf.reader.sync.repository.PersonalizationRepository;
import org.springframework.stereotype.Service;

@Service
public class PersonalizationService extends AbstractSyncService<Personalization, PersonalizationRequest> {

    private final PersonalizationRepository personalizationRepository;

    public PersonalizationService(PersonalizationRepository repository) {
        super(repository, "Personalization");
        this.personalizationRepository = repository;
    }

    /** Convenience read: a user has at most one personalization record. */
    public Personalization findByUserId(String userId) {
        return personalizationRepository.findFirstByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Personalization for user", userId));
    }
}
