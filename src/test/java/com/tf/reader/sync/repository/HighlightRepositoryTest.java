package com.tf.reader.sync.repository;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.sync.model.Highlight;
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
class HighlightRepositoryTest {

    @Autowired
    private HighlightRepository highlightRepository;

    @Test
    void rejectsASecondHighlightForTheSameUserBookAndSpan() {
        highlightRepository.save(newHighlight("highlight-a", "user-500", "book-1"));

        assertThatThrownBy(() -> highlightRepository.save(newHighlight("highlight-b", "user-500", "book-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void allowsReCreatingTheSameSpanOnceTheOriginalIsSoftDeleted() {
        Highlight original = newHighlight("highlight-c", "user-501", "book-1");
        highlightRepository.save(original);

        original.setIsDeleted(true);
        highlightRepository.save(original);

        Highlight recreated = newHighlight("highlight-d", "user-501", "book-1");
        highlightRepository.save(recreated);

        Highlight found = highlightRepository.findById("highlight-d").orElseThrow();
        assertThat(found.getStartLocator().getCfi()).isEqualTo("epubcfi(/6/14!/4/2/2)");
    }

    private static Highlight newHighlight(String id, String userId, String bookId) {
        Highlight highlight = new Highlight();
        highlight.setId(id);
        highlight.setUserId(userId);
        highlight.setBookId(bookId);
        highlight.setStartLocator(Locator.epub("epubcfi(/6/14!/4/2/2)"));
        highlight.setEndLocator(Locator.epub("epubcfi(/6/14!/4/2/10)"));
        return highlight;
    }
}
