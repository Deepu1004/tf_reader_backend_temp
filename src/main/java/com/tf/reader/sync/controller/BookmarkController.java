package com.tf.reader.sync.controller;

import com.tf.reader.sync.dto.BookmarkRequest;
import com.tf.reader.sync.model.Bookmark;
import com.tf.reader.sync.service.BookmarkService;
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
@RequestMapping("/api/v1/bookmarks")
public class BookmarkController {

    private final BookmarkService service;

    public BookmarkController(BookmarkService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Bookmark> create(@Valid @RequestBody BookmarkRequest request) {
        Bookmark created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/bookmarks/" + created.getId())).body(created);
    }

    @GetMapping
    public List<Bookmark> findAll(@RequestParam(required = false) String userId,
                                  @RequestParam(required = false) String bookId,
                                  @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.findAll(userId, bookId, includeDeleted);
    }

    @GetMapping("/{id}")
    public Bookmark findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public Bookmark update(@PathVariable String id, @Valid @RequestBody BookmarkRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Bookmark> delete(@PathVariable String id,
                                           @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            service.purge(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(service.delete(id));
    }

    @PostMapping("/{id}/restore")
    public Bookmark restore(@PathVariable String id) {
        return service.restore(id);
    }
}
