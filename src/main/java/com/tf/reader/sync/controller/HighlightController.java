package com.tf.reader.sync.controller;

import com.tf.reader.sync.dto.HighlightRequest;
import com.tf.reader.sync.model.Highlight;
import com.tf.reader.sync.service.HighlightService;
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
@RequestMapping("/api/v1/highlights")
public class HighlightController {

    private final HighlightService service;

    public HighlightController(HighlightService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Highlight> create(@Valid @RequestBody HighlightRequest request) {
        Highlight created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/highlights/" + created.getId())).body(created);
    }

    @GetMapping
    public List<Highlight> findAll(@RequestParam(required = false) String userId,
                                   @RequestParam(required = false) String bookId,
                                   @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.findAll(userId, bookId, includeDeleted);
    }

    @GetMapping("/{id}")
    public Highlight findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public Highlight update(@PathVariable String id, @Valid @RequestBody HighlightRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Highlight> delete(@PathVariable String id,
                                            @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            service.purge(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(service.delete(id));
    }

    @PostMapping("/{id}/restore")
    public Highlight restore(@PathVariable String id) {
        return service.restore(id);
    }
}
