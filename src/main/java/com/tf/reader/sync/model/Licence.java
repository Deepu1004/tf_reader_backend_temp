package com.tf.reader.sync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A licence granting access to a book for a fixed window.
 *
 * <p>Not part of the offline sync family, so it deliberately does not extend
 * {@link BaseDocument} - there is no LWW clock and no tombstone here.
 */
@Document(collection = "licences")
@CompoundIndex(name = "licence_licid_uk", def = "{'licId': 1}", unique = true)
public class Licence {

    @Id
    private String id;

    /** Licence identifier issued by the entitlement system. */
    private String licId;

    private String bookId;

    /** Start of the licence window. */
    private Instant start;

    /** End of the licence window. {@code null} means the licence never expires. */
    private Instant end;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLicId() {
        return licId;
    }

    public void setLicId(String licId) {
        this.licId = licId;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public Instant getStart() {
        return start;
    }

    public void setStart(Instant start) {
        this.start = start;
    }

    public Instant getEnd() {
        return end;
    }

    public void setEnd(Instant end) {
        this.end = end;
    }
}
