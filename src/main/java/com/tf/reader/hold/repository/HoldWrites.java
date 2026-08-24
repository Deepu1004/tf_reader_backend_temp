package com.tf.reader.hold.repository;

import com.tf.reader.hold.entity.Hold;
import com.tf.reader.hold.entity.HoldStatus;
import com.tf.reader.hold.entity.Offer;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

// Guarded updates — status is IN THE FILTER on every one of these, so the
// condition and the write are a single atomic operation. "Only cancel it if
// it's still queued" is only true when nothing can happen between checking
// and writing — a plain findById-then-save leaves exactly that gap.
//
// Not in HoldRepository because Spring Data's derived-query methods can't
// express findAndModify / findAndRemove with a returned document.
@Repository
public class HoldWrites {

    private final MongoTemplate mongo;

    public HoldWrites(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // QUEUED -> OFFERED. If something else already moved it, this matches
    // nothing and the caller sees empty rather than clobbering a status.
    public Optional<Hold> offerIfQueued(String holdId, Offer offer) {
        Query q = Query.query(Criteria.where("holdId").is(holdId).and("status").is(HoldStatus.QUEUED));
        Update u = new Update().set("status", HoldStatus.OFFERED).set("offer", offer);
        Hold updated = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), Hold.class);
        return Optional.ofNullable(updated);
    }

    // Accept wins the referee: OFFERED and the deadline still in the future.
    // "Still offered" alone isn't enough — a turn can be over before the
    // sweep has got to it. findAndRemove makes winning and deleting the hold
    // one operation.
    public Optional<Hold> claimIfLive(String holdId, String userId, Instant now) {
        Query q = Query.query(Criteria.where("holdId").is(holdId)
                .and("userId").is(userId)
                .and("status").is(HoldStatus.OFFERED)
                .and("offer.expiresAt").gt(now));
        return Optional.ofNullable(mongo.findAndRemove(q, Hold.class));
    }

    // The sweep's half of the same race. OFFERED and the deadline has
    // passed — the loser of the race with claimIfLive matches nothing here.
    public Optional<Hold> expireIfLapsed(String holdId, Instant now) {
        Query q = Query.query(Criteria.where("holdId").is(holdId)
                .and("status").is(HoldStatus.OFFERED)
                .and("offer.expiresAt").lte(now));
        return Optional.ofNullable(mongo.findAndRemove(q, Hold.class));
    }

    // Cancel — the contract's only genuine hard delete. Filtering on the
    // caller's own userId means a repeat call, or a stranger's guess at a
    // holdId, both touch nothing rather than throwing.
    public Optional<Hold> deleteOwn(String holdId, String userId) {
        Query q = Query.query(Criteria.where("holdId").is(holdId).and("userId").is(userId));
        return Optional.ofNullable(mongo.findAndRemove(q, Hold.class));
    }
}
