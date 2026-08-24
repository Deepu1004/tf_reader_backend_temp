package com.tf.reader.hold.controller;

import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.hold.api.AvailabilityQuery;
import com.tf.reader.hold.api.AvailabilitySnapshot;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// The HTTP face of AvailabilityQuery — exists because the catalogue team
// doesn't call the interface while building a feed: their copies object
// carries total only, and says to fetch available here.
//
// ALWAYS 200. Never a 404, even for an item never heard of — absence of a
// number means unknown, and the app renders the metadata with no Read button.
@RestController
public class AvailabilityController {

    private final AvailabilityQuery availability;
    private final EntitlementQuery entitlements;

    public AvailabilityController(AvailabilityQuery availability, EntitlementQuery entitlements) {
        this.availability = availability;
        this.entitlements = entitlements;
    }

    @GetMapping("/api/v1/items/{itemId}/availability")
    public AvailabilitySnapshot forItem(@AuthenticationPrincipal CurrentUser me, @PathVariable String itemId) {
        Integer copies;
        try {
            copies = entitlements.check(new SubjectRef(me.userId(), me.institutionId()), itemId).copies();
        } catch (RuntimeException e) {
            copies = null; // unknown, not unlimited — but the response is identical either way
        }
        return availability.forItem(me.institutionId(), itemId, copies);
    }
}
