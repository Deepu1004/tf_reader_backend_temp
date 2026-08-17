package com.tf.reader.catalogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.InstitutionRepository;

class CatalogueVersionBumperTest {

    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final EntitlementRepository entitlementRepository = mock(EntitlementRepository.class);
    private final CatalogueVersionBumper bumper =
            new CatalogueVersionBumper(institutionRepository, entitlementRepository);

    @BeforeEach
    void stubInstitutionLookups() {
        when(institutionRepository.findById(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Institution institution = new Institution();
            institution.setId(id);
            institution.setCatalogueVersion(4L);
            return Optional.of(institution);
        });
    }

    @Test
    void bumpsEveryActivelyEntitledInstitutionForEachScope() {
        when(entitlementRepository.findByScopeTypeAndScopeId(ScopeType.COLLECTION, "col_1"))
                .thenReturn(List.of(
                        entitlement("inst_a", EntitlementStatus.ACTIVE),
                        entitlement("inst_b", EntitlementStatus.SUSPENDED)));

        bumper.bump(CatalogueVersionBumper.Scope.COLLECTION, "col_1");

        ArgumentCaptor<Institution> saved = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("inst_a");
        assertThat(saved.getValue().getCatalogueVersion()).isEqualTo(5L);
    }

    @Test
    void bumpsTheInstitutionDirectlyWithoutAnEntitlementLookup() {
        bumper.bump(CatalogueVersionBumper.Scope.INSTITUTION, "inst_c");

        ArgumentCaptor<Institution> saved = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("inst_c");
        assertThat(saved.getValue().getCatalogueVersion()).isEqualTo(5L);
        verify(entitlementRepository, never()).findByScopeTypeAndScopeId(any(), any());
    }

    @Test
    void doesNothingWhenNoInstitutionHasAnActiveEntitlementForTheScope() {
        when(entitlementRepository.findByScopeTypeAndScopeId(ScopeType.PUBLISHER, "pub_1"))
                .thenReturn(List.of());

        bumper.bump(CatalogueVersionBumper.Scope.PUBLISHER, "pub_1");

        verify(institutionRepository, never()).save(any());
    }

    private static Entitlement entitlement(String institutionId, EntitlementStatus status) {
        Entitlement entitlement = new Entitlement();
        entitlement.setInstitutionId(institutionId);
        entitlement.setStatus(status);
        return entitlement;
    }

}
