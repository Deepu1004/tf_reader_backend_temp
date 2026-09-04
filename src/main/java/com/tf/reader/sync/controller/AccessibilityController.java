package com.tf.reader.sync.controller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

import com.tf.reader.sync.dto.AccessibilityRequest;
import com.tf.reader.sync.model.Accessibility;
import com.tf.reader.sync.service.AccessibilityService;

import jakarta.validation.Valid;

/**
 * Accessibility is user-scoped, so there is no bookId filter here.
 */
@RestController
@RequestMapping("/api/v1/accessibility")
public class AccessibilityController {

    private final AccessibilityService service;

    public AccessibilityController(AccessibilityService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Accessibility> create(@Valid @RequestBody AccessibilityRequest request) {
        Accessibility created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/accessibility/" + created.getId())).body(created);
    }

    @GetMapping
    public List<Accessibility> findAll(@RequestParam(required = false) String userId,
                                       @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.findAll(userId, includeDeleted);
    }

    @GetMapping("/{id}")
    public Accessibility findById(@PathVariable String id) {
        
        return service.findById(id);
    }

    /** A user has at most one accessibility record, so this returns it directly. */
    @GetMapping("/user/{userId}")
    public Accessibility findByUserId(@PathVariable String userId) {
        return service.findByUserId(userId);
    }

    @PutMapping("/{id}")
    public Accessibility update(@PathVariable String id, @Valid @RequestBody AccessibilityRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Accessibility> delete(@PathVariable String id,
                                                @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            service.purge(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(service.delete(id));
    }

    @PostMapping("/{id}/restore")
    public Accessibility restore(@PathVariable String id) {

        return service.restore(id);
    }


}
