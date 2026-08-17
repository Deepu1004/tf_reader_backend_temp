package com.tf.reader.catalogue.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.InstitutionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Bumps Institution.catalogueVersion so a cached catalogue is known to be stale.
 *
 * <p>Only the scope that actually changed is bumped: no cascading collection to items or item to
 * collection, because only one thing changes per call this week.
 */
@Service
@RequiredArgsConstructor
public class CatalogueVersionBumper {

    private final InstitutionRepository institutionRepository;
    private final EntitlementRepository entitlementRepository;

    public enum Scope {
        INSTITUTION,
        PUBLISHER,
        COLLECTION,
        ITEM
    }

    public void bump(Scope scope, String scopeId) {
        List<String> institutionIds = switch (scope) {
            case INSTITUTION -> List.of(scopeId);
            case PUBLISHER -> entitledInstitutionIds(ScopeType.PUBLISHER, scopeId);
            case COLLECTION -> entitledInstitutionIds(ScopeType.COLLECTION, scopeId);
            case ITEM -> entitledInstitutionIds(ScopeType.ITEM, scopeId);
        };

        institutionIds.forEach(this::bumpOne);
    }

    private List<String> entitledInstitutionIds(ScopeType scopeType, String scopeId) {
        return entitlementRepository.findByScopeTypeAndScopeId(scopeType, scopeId).stream()
                .filter(entitlement -> entitlement.getStatus() == EntitlementStatus.ACTIVE)
                .map(Entitlement::getInstitutionId)
                .distinct()
                .toList();
    }

    private void bumpOne(String institutionId) {
        institutionRepository.findById(institutionId).ifPresent(institution -> {
            institution.setCatalogueVersion(institution.getCatalogueVersion() + 1);
            institution.setUpdatedAt(Instant.now());
            institutionRepository.save(institution);
        });
    }

}
