package com.tf.reader.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.EntitlementRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EntitlementRepositoryTest {

	@Autowired
	private EntitlementRepository entitlementRepository;

	private Entitlement newEntitlement(String institutionId, ScopeType scopeType, String scopeId) {
		Entitlement entitlement = new Entitlement();
		entitlement.setInstitutionId(institutionId);
		entitlement.setScopeType(scopeType);
		entitlement.setScopeId(scopeId);
		entitlement.setStatus(EntitlementStatus.ACTIVE);
		return entitlement;
	}

	@Test
	void savesAndReadsBackAnEntitlement() {
		Entitlement saved = entitlementRepository.save(
				newEntitlement("inst_7f3", ScopeType.COLLECTION, "col_law2024"));
		Entitlement found = entitlementRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getInstitutionId()).isEqualTo("inst_7f3");
		assertThat(found.getScopeType()).isEqualTo(ScopeType.COLLECTION);
	}

	@Test
	void rejectsADuplicateEntitlementForTheSameInstitutionScopeAndScopeId() {
		entitlementRepository.save(newEntitlement("inst_dupe", ScopeType.PUBLISHER, "pub_rtlg"));

		assertThatThrownBy(() -> entitlementRepository.save(newEntitlement("inst_dupe", ScopeType.PUBLISHER, "pub_rtlg")))
				.isInstanceOf(DuplicateKeyException.class);
	}

}
