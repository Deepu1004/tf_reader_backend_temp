package com.tf.reader.hold.repository;

// Superseded — Offer is embedded in Hold (see entity/Offer.java), not its
// own Mongo document, so there is no repository to write here. Kept as an
// empty placeholder rather than deleted; safe to remove.
//
// The reason: accept and the sweep race each other, and a single guarded
// update on ONE document (HoldWrites) is what makes that race provable. A
// second collection would turn it into an unprovable two-document operation.
public interface OfferRepository {
}
