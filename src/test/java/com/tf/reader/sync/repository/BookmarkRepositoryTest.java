package com.tf.reader.sync.repository;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.sync.model.Bookmark;
import com.tf.reader.sync.model.Locator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookmarkRepositoryTest {

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Test
    void rejectsASecondBookmarkForTheSameUserBookAndLocator() {
        bookmarkRepository.save(newBookmark("bookmark-a", "user-600", "book-1"));

        assertThatThrownBy(() -> bookmarkRepository.save(newBookmark("bookmark-b", "user-600", "book-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void allowsReCreatingTheSameLocatorOnceTheOriginalIsSoftDeleted() {
        Bookmark original = newBookmark("bookmark-c", "user-601", "book-1");
        bookmarkRepository.save(original);

        original.setIsDeleted(true);
        bookmarkRepository.save(original);

        Bookmark recreated = newBookmark("bookmark-d", "user-601", "book-1");
        bookmarkRepository.save(recreated);

        Bookmark found = bookmarkRepository.findById("bookmark-d").orElseThrow();
        assertThat(found.getLocator().getCfi()).isEqualTo("epubcfi(/6/14!/4/2/2)");
    }

    private static Bookmark newBookmark(String id, String userId, String bookId) {
        Bookmark bookmark = new Bookmark();
        bookmark.setId(id);
        bookmark.setUserId(userId);
        bookmark.setBookId(bookId);
        bookmark.setLocator(Locator.epub("epubcfi(/6/14!/4/2/2)"));
        return bookmark;
    }
}
