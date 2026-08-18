package com.tf.reader.sync.service;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.sync.dto.LicenceRequest;
import com.tf.reader.sync.exception.DuplicateResourceException;
import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Licence;
import com.tf.reader.sync.repository.LicenceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LicenceService {

    private final LicenceRepository repository;

    public LicenceService(LicenceRepository repository) {
        this.repository = repository;
    }

    /** IDs may be client-supplied; the server fills in a missing one. */
    public Licence create(LicenceRequest request) {
        Licence licence = request.toDocument();
        if (licence.getId() == null || licence.getId().isBlank()) {
            licence.setId(UUID.randomUUID().toString());
        } else if (repository.existsById(licence.getId())) {
            throw new DuplicateResourceException("Licence", licence.getId());
        }
        if (repository.findFirstByLicId(licence.getLicId()).isPresent()) {
            throw new DuplicateResourceException("Licence with licId '" + licence.getLicId() + "' already exists");
        }
        if (licence.getEnd() != null && licence.getEnd().isBefore(licence.getStart())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "end must not be before start");
        }
        return repository.save(licence);
    }

    public List<Licence> findByBookId(String bookId) {
        return repository.findByBookId(bookId);
    }

    /**
     * True once the licence window has closed. A licence with no {@code end}
     * never expires.
     */
    public boolean isExpired(String licId) {
        Licence licence = repository.findFirstByLicId(licId)
                .orElseThrow(() -> new ResourceNotFoundException("Licence", licId));
        return hasEnded(licence, Instant.now());
    }

    /**
     * True when every licence held for the book has ended. A book keeps its
     * access as long as one licence is still open, so a renewal wins over an
     * expired predecessor.
     */
    public boolean isExpiredForBook(String bookId) {
        List<Licence> licences = repository.findByBookId(bookId);
        if (licences.isEmpty()) {
            throw new ResourceNotFoundException("Licence for book", bookId);
        }
        Instant now = Instant.now();
        return licences.stream().allMatch(licence -> hasEnded(licence, now));
    }

    /** A licence with no {@code end} never expires. */
    private boolean hasEnded(Licence licence, Instant now) {
        return licence.getEnd() != null && licence.getEnd().isBefore(now);
    }
}
