package com.tf.reader.sync.service;

import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Accessibility;
import com.tf.reader.sync.repository.AccessibilityRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessibilityServiceTest {

    private AccessibilityRepository repository;
    private AccessibilityService service;

    @BeforeEach
    void setUp() {
        repository = mock(AccessibilityRepository.class);
        service = new AccessibilityService(repository);
    }

    @Test
    void findByUserIdReturnsTheUsersSingleRecord() {
        Accessibility stored = new Accessibility();
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
