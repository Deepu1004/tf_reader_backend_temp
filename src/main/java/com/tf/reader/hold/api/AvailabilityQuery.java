package com.tf.reader.hold.api;

// Published contract: how many copies are free right now? The catalogue
// team calls this while building a feed — their copies object carries
// total only, and says to fetch available here.
//
// Never throws for a data reason, and never fails the caller — a book page
// has to render regardless. copies == null covers BOTH "the title has no
// copy limit" and "entitlement couldn't be checked": either way the honest
// answer is the same, omit available rather than guess.
public interface AvailabilityQuery {
    AvailabilitySnapshot forItem(String scope, String itemId, Integer copies);
}
