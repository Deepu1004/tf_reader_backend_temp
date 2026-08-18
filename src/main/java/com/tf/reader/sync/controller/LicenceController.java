package com.tf.reader.sync.controller;

import com.tf.reader.sync.dto.LicenceRequest;
import com.tf.reader.sync.model.Licence;
import com.tf.reader.sync.service.LicenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/licences")
public class LicenceController {

    private final LicenceService service;

    public LicenceController(LicenceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Licence> create(@Valid @RequestBody LicenceRequest request) {
        Licence created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/licences/" + created.getId())).body(created);
    }

    /** Every licence recorded for the book, expired ones included. */
    @GetMapping("/book/{bookId}")
    public List<Licence> findByBookId(@PathVariable String bookId) {
        return service.findByBookId(bookId);
    }

    /** {@code true} if the licence has ended, {@code false} if it is still open. */
    @GetMapping("/{licId}/expired")
    public boolean isExpired(@PathVariable String licId) {
        return service.isExpired(licId);
    }

    /** {@code true} only if every licence held for the book has ended. */
    @GetMapping("/book/{bookId}/expired")
    public boolean isExpiredForBook(@PathVariable String bookId) {
        return service.isExpiredForBook(bookId);
    }
}
