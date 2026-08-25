package com.tf.reader.catalogue.opds.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.opds.service.OpdsPublicFeedService;
import com.tf.reader.common.page.PageQuery;

/**
 * The no-sign-in entry point into the app (Workstream 9): every open access title, and a
 * discovery search across everything published, entitled or not. Thin by design, same split
 * as {@link OpdsCatalogueController} - dispatch here, feed-building in
 * {@link OpdsPublicFeedService}.
 */
@RestController
@RequestMapping("/opds/v1/public")
public class OpdsPublicCatalogueController {

    private static final String OPDS_MEDIA_TYPE = "application/opds+json";

    private final OpdsPublicFeedService publicFeedService;

    public OpdsPublicCatalogueController(OpdsPublicFeedService publicFeedService) {
        this.publicFeedService = publicFeedService;
    }

    @GetMapping(value = "/catalogue", produces = OPDS_MEDIA_TYPE)
    public ResponseEntity<OpdsPublicationFeed> catalogue(PageQuery page) {
        return ok(publicFeedService.catalogueFeed(page));
    }

    // Same result for every caller (no institution, no entitlement), so a shared, global cache
    // is correct here - not keyed on anything, unlike an institution feed's per-institution ETag.
    @GetMapping(value = "/search", produces = OPDS_MEDIA_TYPE)
    public ResponseEntity<OpdsPublicationFeed> search(
            @RequestParam String query,
            PageQuery page,
            @RequestParam(required = false) ContentType contentType,
            @RequestParam(required = false) AccessTier accessTier) {
        return ok(publicFeedService.searchFeed(query, page, contentType, accessTier));
    }

    private ResponseEntity<OpdsPublicationFeed> ok(OpdsPublicationFeed feed) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(feed);
    }
}
