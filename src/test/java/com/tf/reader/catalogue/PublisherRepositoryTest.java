package com.tf.reader.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;

import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest(properties = "tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET)
class PublisherRepositoryTest extends ContainerisedInfrastructure {

	@Autowired
	private PublisherRepository publisherRepository;

	@AfterEach
	void removeWhatThisTestWrote() {
		publisherRepository.deleteById("pub_ownmongo");
		publisherRepository.deleteById("pub_nomongo");
	}

	@Test
	void findsOnlyPublishersWithTheirOwnMongoConfigured() {
		Publisher ownMongo = new Publisher("pub_ownmongo", "OWNMONGO", "Own Mongo Press", null, null,
				RecordStatus.ACTIVE, Instant.now(), Instant.now());
		ownMongo.setMongoUri("mongodb://localhost:27018/pub_ownmongo");
		publisherRepository.save(ownMongo);
		publisherRepository.save(new Publisher("pub_nomongo", "NOMONGO", "Shared Database Press", null, null,
				RecordStatus.ACTIVE, Instant.now(), Instant.now()));

		List<Publisher> found = publisherRepository.findByMongoUriIsNotNull();

		assertThat(found).extracting(Publisher::getId).contains("pub_ownmongo").doesNotContain("pub_nomongo");
	}

	@Test
	void savesAndReadsBackAPublisher() {
		Publisher publisher = new Publisher(null, "RTLG", "Routledge", "Academic imprint",
				"https://cdn.tf/logos/rtlg.png", RecordStatus.ACTIVE, Instant.now(), Instant.now());

		Publisher saved = publisherRepository.save(publisher);
		Publisher found = publisherRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getCode()).isEqualTo("RTLG");
		assertThat(found.getName()).isEqualTo("Routledge");
	}

	@Test
	void rejectsASecondPublisherWithTheSameCode() {
		publisherRepository.save(new Publisher(null, "DUPE", "First", null, null,
				RecordStatus.ACTIVE, Instant.now(), Instant.now()));

		assertThatThrownBy(() -> publisherRepository.save(new Publisher(null, "DUPE", "Second", null, null,
				RecordStatus.ACTIVE, Instant.now(), Instant.now())))
				.isInstanceOf(DuplicateKeyException.class);
	}

}
