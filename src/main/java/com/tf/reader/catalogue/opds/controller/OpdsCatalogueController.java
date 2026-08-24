package com.tf.reader.catalogue.opds.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.opds.dto.OpdsNavigationFeed;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.opds.service.OpdsFeedService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.page.PageQuery;

/**
 * The reader app's catalogue feeds (Workstream 5): the root feed and one shelf/the whole
 * entitled catalogue. Thin by design - identity is the authenticated {@link CurrentUser}
 * the app resource-server chain resolves (same pattern as {@code LoanController}), the
 * institution-mismatch check and the ETag short-circuit live here, everything else is
 * {@link OpdsFeedService}.
 */
@RestController
@RequestMapping("/opds/v1/institutions/{institutionId}")
public class OpdsCatalogueController {

    private static final String OPDS_MEDIA_TYPE = "application/opds+json";

    private final OpdsFeedService feedService;

    public OpdsCatalogueController(OpdsFeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping(value = "/catalogue", produces = OPDS_MEDIA_TYPE)
    public ResponseEntity<OpdsNavigationFeed> rootFeed(
            @PathVariable String institutionId,
            @AuthenticationPrincipal CurrentUser caller,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        requireMatchingInstitution(caller, institutionId);
        Institution institution = feedService.loadInstitution(institutionId);
        String etag = etagFor(institution);
        if (matches(etag, ifNoneMatch)) {
            return notModified(etag);
        }

        SubjectRef subject = new SubjectRef(caller.userId(), institutionId);
        return ok(feedService.rootFeed(institution, subject), etag);
    }

    @GetMapping(value = "/groups/{groupId}", produces = OPDS_MEDIA_TYPE)
    public ResponseEntity<OpdsPublicationFeed> groupFeed(
            @PathVariable String institutionId,
            @PathVariable String groupId,
            @AuthenticationPrincipal CurrentUser caller,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            PageQuery page,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) ContentType contentType,
            @RequestParam(required = false) AccessTier accessTier) {

        requireMatchingInstitution(caller, institutionId);
        Institution institution = feedService.loadInstitution(institutionId);
        String etag = etagFor(institution);
        if (matches(etag, ifNoneMatch)) {
            return notModified(etag);
        }

        SubjectRef subject = new SubjectRef(caller.userId(), institutionId);
        OpdsPublicationFeed feed = feedService.groupFeed(institution, groupId, subject, page, sort, contentType,
                accessTier);
        return ok(feed, etag);
    }

    private void requireMatchingInstitution(CurrentUser caller, String institutionId) {
        if (!institutionId.equals(caller.institutionId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_INSTITUTION_MISMATCH,
                    "This token belongs to a different institution");
        }
    }

    // Weak - this is derived data, not a byte-identical resource. Keyed on catalogueVersion
    // per wokay-api.yaml (frozen contract, not our choice to change), so any admin mutation
    // that bumps it invalidates every cached copy for the whole institution - not per user.
    // This is only correct because the feed is also entitlement-personalised: it relies on
    // EntitlementAdminService bumping catalogueVersion on every entitlement create/update
    // (see CatalogueVersionBumper.Scope.INSTITUTION there). If a future entitlement mutation
    // path is added that skips that bumper, a cached 304 could serve a stale entitlement view.
    private String etagFor(Institution institution) {
        return "W/\"" + institution.getId() + "-" + institution.getCatalogueVersion() + "\"";
    }

    // RFC 7232: If-None-Match may be "*" (matches any current representation) or a
    // comma-separated list of etags, not just the single value we happen to emit.
    private boolean matches(String etag, String ifNoneMatch) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        if ("*".equals(ifNoneMatch.trim())) {
            return true;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            if (etag.equals(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private <T> ResponseEntity<T> ok(T body, String etag) {
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cacheControl())
                .body(body);
    }

    private <T> ResponseEntity<T> notModified(String etag) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(cacheControl())
                .build();
    }

    private CacheControl cacheControl() {
        return CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate();
    }
}
