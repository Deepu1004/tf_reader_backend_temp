package com.tf.reader.sync.controller;

import com.tf.reader.sync.dto.PersonalizationRequest;
import com.tf.reader.sync.model.Personalization;
import com.tf.reader.sync.service.PersonalizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Personalization is user-scoped, so there is no bookId filter here.
 */
@RestController
@RequestMapping("/api/v1/personalization")
public class PersonalizationController {

    private final PersonalizationService service;

    public PersonalizationController(PersonalizationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Personalization> create(@Valid @RequestBody PersonalizationRequest request) {
        Personalization created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/personalization/" + created.getId())).body(created);
    }

    @GetMapping
    public List<Personalization> findAll(@RequestParam(required = false) String userId,
                                         @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.findAll(userId, includeDeleted);
    }

    @GetMapping("/{id}")
    public Personalization findById(@PathVariable String id) {
        return service.findById(id);
    }

    /** A user has at most one personalization record, so this returns it directly. */
    @GetMapping("/user/{userId}")
    public Personalization findByUserId(@PathVariable String userId) {
        return service.findByUserId(userId);
    }

    @PutMapping("/{id}")
    public Personalization update(@PathVariable String id,
                                  @Valid @RequestBody PersonalizationRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Personalization> delete(@PathVariable String id,
                                                  @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            service.purge(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(service.delete(id));
    }

    @PostMapping("/{id}/restore")
    public Personalization restore(@PathVariable String id) {
        return service.restore(id);
    }
}
