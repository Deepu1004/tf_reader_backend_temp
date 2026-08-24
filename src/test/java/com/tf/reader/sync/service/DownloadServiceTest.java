package com.tf.reader.sync.service;

import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Download;
import com.tf.reader.sync.model.DownloadFormat;
import com.tf.reader.sync.repository.DownloadRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownloadServiceTest {

    private DownloadRepository repository;
    private DownloadService service;

    @BeforeEach
    void setUp() {
        repository = mock(DownloadRepository.class);
        service = new DownloadService(repository);
    }

    @Test
    void updateIsValidFlipsIsValidOnEveryFormatTheUserHoldsForTheBook() {
        Download pdf = download(DownloadFormat.PDF);
        Download epub = download(DownloadFormat.EPUB);
        when(repository.findByUserIdAndBookIdAndIsDeletedFalse("user-001", "book-001"))
                .thenReturn(List.of(pdf, epub));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Download> updated = service.updateIsValid("user-001", "book-001", false);

        assertThat(updated).extracting(Download::getIsValid).containsExactly(false, false);
    }

    @Test
    void updateIsValidThrowsNotFoundWhenTheUserHasNoDownloadForTheBook() {
        when(repository.findByUserIdAndBookIdAndIsDeletedFalse("user-002", "book-002"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.updateIsValid("user-002", "book-002", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Download download(DownloadFormat format) {
        Download download = new Download();
        download.setUserId("user-001");
        download.setBookId("book-001");
        download.setFormat(format);
        download.setIsValid(true);
        return download;
    }
}
