package com.tf.reader.sync.controller;

import com.tf.reader.sync.dto.DownloadRequest;
import com.tf.reader.sync.dto.DownloadValidityRequest;
import com.tf.reader.sync.model.Download;
import com.tf.reader.sync.service.DownloadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/downloads")
public class DownloadController {

    private final DownloadService service;

    public DownloadController(DownloadService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Download> create(@Valid @RequestBody DownloadRequest request) {
        Download created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/downloads/" + created.getId())).body(created);
    }

    @GetMapping
    public List<Download> findAll(@RequestParam(required = false) String userId,
                                  @RequestParam(required = false) String bookId,
                                  @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.findAll(userId, bookId, includeDeleted);
    }

    @GetMapping("/{id}")
    public Download findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public Download update(@PathVariable String id, @Valid @RequestBody DownloadRequest request) {
        return service.update(id, request);
    }

    /** Flips isValid for every download the user holds for the book, across all formats. */
    @PatchMapping
    public List<Download> updateIsValid(@RequestParam String userId,
                                        @RequestParam String bookId,
                                        @Valid @RequestBody DownloadValidityRequest request) {
        return service.updateIsValid(userId, bookId, request.isValid());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Download> delete(@PathVariable String id,
                                           @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            service.purge(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(service.delete(id));
    }

    @PostMapping("/{id}/restore")
    public Download restore(@PathVariable String id) {
        return service.restore(id);
    }
}
