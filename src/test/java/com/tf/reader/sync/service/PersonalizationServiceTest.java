package com.tf.reader.sync.service;

import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Personalization;
import com.tf.reader.sync.repository.PersonalizationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Personalization became a per-user singleton alongside accessibility - this covers the
 * {@code findByUserId} convenience read that makes that true.
 */
class PersonalizationServiceTest {

    private PersonalizationRepository repository;
    private PersonalizationService service;

    @BeforeEach
    void setUp() {
        repository = mock(PersonalizationRepository.class);
        service = new PersonalizationService(repository);
    }

    @Test
    void findByUserIdReturnsTheUsersSingleRecord() {
        Personalization stored = new Personalization();
        stored.setUserId("user-001");
        when(repository.findFirstByUserIdAndIsDeletedFalse("user-001")).thenReturn(Optional.of(stored));

        assertThat(service.findByUserId("user-001")).isSameAs(stored);
    }

    @Test
    void findByUserIdThrowsNotFoundWhenTheUserHasNoRecord() {
        when(repository.findFirstByUserIdAndIsDeletedFalse("user-002")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByUserId("user-002"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
