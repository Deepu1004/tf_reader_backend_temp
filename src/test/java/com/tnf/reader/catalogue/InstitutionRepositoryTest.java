package com.tnf.reader.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import com.tnf.reader.TestcontainersConfiguration;
import com.tnf.reader.catalogue.entity.Institution;
import com.tnf.reader.catalogue.repository.InstitutionRepository;
import com.tnf.reader.common.model.RecordStatus;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InstitutionRepositoryTest {

	@Autowired
	private InstitutionRepository institutionRepository;

	private Institution newInstitution(String code, String name) {
		Institution institution = new Institution();
		institution.setCode(code);
		institution.setName(name);
		institution.setStatus(RecordStatus.ACTIVE);
		return institution;
	}

	@Test
	void savesAndReadsBackAnInstitution() {
		Institution saved = institutionRepository.save(newInstitution("imperial", "Imperial College London"));
		Institution found = institutionRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getCode()).isEqualTo("imperial");
		assertThat(found.getName()).isEqualTo("Imperial College London");
	}

	@Test
	void rejectsASecondInstitutionWithTheSameCode() {
		institutionRepository.save(newInstitution("dupe", "First"));

		assertThatThrownBy(() -> institutionRepository.save(newInstitution("dupe", "Second")))
				.isInstanceOf(DuplicateKeyException.class);
	}

}
