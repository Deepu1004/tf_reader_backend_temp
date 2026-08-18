package com.tf.reader.sync.service;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.sync.dto.ProgressRequest;
import com.tf.reader.sync.exception.DuplicateResourceException;
import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Progress;
import com.tf.reader.sync.repository.ProgressRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the generic CRUD in {@link AbstractSyncService} and the book-scoped filtering in
 * {@link BookScopedSyncService} through a concrete subclass, since neither abstract class can be
 * instantiated on its own and Progress adds no behaviour of its own on top of them.
 */
class ProgressServiceTest {

    private ProgressRepository repository;
    private ProgressService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProgressRepository.class);
        service = new ProgressService(repository);
    }

    private static ProgressRequest request(String id, long offset) {
        return new ProgressRequest(id, "user-001", "book-001", offset, null);
    }

    // ---- create ----

    @Test
    void createGeneratesAnIdAndStampsTimestampsWhenNoneIsSupplied() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Progress created = service.create(request(null, 31L));

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getIsDeleted()).isFalse();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(created.getOffset()).isEqualTo(31L);
    }

    @Test
    void createRejectsAClientSuppliedIdThatAlreadyExists() {
        when(repository.existsById("progress-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("progress-1", 31L)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // ---- findById ----

    @Test
    void findByIdReturnsTheStoredRecord() {
        Progress stored = new Progress();
        stored.setId("progress-1");
        when(repository.findById("progress-1")).thenReturn(Optional.of(stored));

        assertThat(service.findById("progress-1")).isSameAs(stored);
    }

    @Test
    void findByIdThrowsNotFoundForAnUnknownId() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- update ----

    @Test
    void updateAppliesTheRequestAndBumpsUpdatedAt() {
        Progress existing = new Progress();
        existing.setId("progress-1");
        existing.setIsDeleted(false);
        existing.setOffset(10L);
        when(repository.findById("progress-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Progress updated = service.update("progress-1", request("progress-1", 50L));

        assertThat(updated.getOffset()).isEqualTo(50L);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateRefusesATombstonedRecord() {
        Progress deleted = new Progress();
        deleted.setId("progress-1");
        deleted.setIsDeleted(true);
        when(repository.findById("progress-1")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update("progress-1", request("progress-1", 50L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- delete / purge ----

    @Test
    void deleteWritesATombstoneRatherThanRemovingTheRow() {
        Progress existing = new Progress();
        existing.setId("progress-1");
        when(repository.findById("progress-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Progress deleted = service.delete("progress-1");

        assertThat(deleted.getIsDeleted()).isTrue();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void purgeThrowsNotFoundForAnUnknownId() {
        when(repository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.purge("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- findAll(userId, bookId) ----

    @Test
    void findAllFiltersByUserAndBookWhenBothAreGiven() {
        Progress match = new Progress();
        when(repository.findByUserIdAndBookIdAndIsDeletedFalse("user-001", "book-001"))
                .thenReturn(List.of(match));

        List<Progress> results = service.findAll("user-001", "book-001", false);

        assertThat(results).containsExactly(match);
    }

    @Test
    void findAllRejectsABookIdFilterWithoutAUserId() {
        assertThatThrownBy(() -> service.findAll(null, "book-001", false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
