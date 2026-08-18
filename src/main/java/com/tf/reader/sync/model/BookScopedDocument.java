package com.tf.reader.sync.model;

/**
 * Base for collections scoped to a user <em>and</em> a book.
 * Accessibility is user-scoped only and therefore does not extend this class.
 */
public abstract class BookScopedDocument extends BaseDocument {

    private String bookId;

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }
}
