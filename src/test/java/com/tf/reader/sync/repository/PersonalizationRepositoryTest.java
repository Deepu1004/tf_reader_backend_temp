package com.tf.reader.sync.repository;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.sync.model.Personalization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PersonalizationRepositoryTest {

    @Autowired
    private PersonalizationRepository personalizationRepository;

    @Test
    void findsTheOneRecordForAUser() {
        Personalization saved = personalizationRepository.save(newPersonalization("user-100"));

        Personalization found = personalizationRepository
                .findFirstByUserIdAndIsDeletedFalse("user-100")
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    /** Two devices minting different ids for the same user must still collide, not shadow each other. */
    @Test
    void rejectsASecondPersonalizationRecordForTheSameUserEvenWithADifferentId() {
        personalizationRepository.save(newPersonalization("user-200"));

        assertThatThrownBy(() -> personalizationRepository.save(newPersonalization("user-200")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private static Personalization newPersonalization(String userId) {
        Personalization personalization = new Personalization();
        personalization.setId("prefs-" + userId + "-" + UUID.randomUUID());
        personalization.setUserId(userId);
        return personalization;
    }
}
