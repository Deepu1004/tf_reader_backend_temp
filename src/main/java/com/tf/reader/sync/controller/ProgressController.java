package com.tf.reader.sync.controller;

import com.tf.reader.sync.dto.ProgressRequest;
import com.tf.reader.sync.model.Progress;
import com.tf.reader.sync.service.ProgressService;
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

@RestController
@RequestMapping("/api/v1/progress")
public class ProgressController {

    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Progress> create(@Valid @RequestBody ProgressRequest request) {
        Progress created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/progress/" + created.getId())).body(created);
    }

    @GetMapping
    public List<Progress> findAll(@RequestParam(required = false) String userId,
                                  @RequestParam(required = false) String bookId,
                                  @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.findAll(userId, bookId, includeDeleted);
    }

    @GetMapping("/{id}")
    public Progress findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public Progress update(@PathVariable String id, @Valid @RequestBody ProgressRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Progress> delete(@PathVariable String id,
                                           @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            service.purge(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(service.delete(id));
    }

    @PostMapping("/{id}/restore")
    public Progress restore(@PathVariable String id) {
        return service.restore(id);
    }
}
