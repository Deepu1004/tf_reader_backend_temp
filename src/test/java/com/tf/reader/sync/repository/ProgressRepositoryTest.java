package com.tf.reader.sync.repository;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.sync.model.Locator;
import com.tf.reader.sync.model.Progress;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProgressRepositoryTest {

    @Autowired
    private ProgressRepository progressRepository;

    @Test
    void savesAndReadsBackAStructuredEpubLocatorWithAZeroOffset() {
        Progress progress = new Progress();
        progress.setId("progress-locator-1");
        progress.setUserId("user-300");
        progress.setBookId("book-epub-1");
        progress.setOffset(0L);
        progress.setLocator(Locator.epub("epubcfi(/6/14!/4/2/2)"));

        progressRepository.save(progress);
        Progress found = progressRepository.findById("progress-locator-1").orElseThrow();

        assertThat(found.getLocator().getType()).isEqualTo("EPUB");
        assertThat(found.getLocator().getCfi()).isEqualTo("epubcfi(/6/14!/4/2/2)");
        assertThat(found.getOffset()).isZero();
    }

    @Test
    void rejectsASecondProgressRecordForTheSameUserAndBook() {
        progressRepository.save(newProgress("progress-a", "user-400", "book-1"));

        assertThatThrownBy(() -> progressRepository.save(newProgress("progress-b", "user-400", "book-1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private static Progress newProgress(String id, String userId, String bookId) {
        Progress progress = new Progress();
        progress.setId(id);
        progress.setUserId(userId);
        progress.setBookId(bookId);
        progress.setOffset(1L);
        return progress;
    }
}
