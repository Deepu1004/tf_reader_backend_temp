package com.tf.reader.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;

@SpringBootTest(properties = "tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET)
class CatalogueItemRepositoryTest extends ContainerisedInfrastructure {

	@Autowired
	private CatalogueItemRepository catalogueItemRepository;

	@Autowired
	private PublisherRepository publisherRepository;

	private CatalogueItem newItem(String publisherId, List<String> collectionIds) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setCollectionIds(collectionIds);
		item.setTitle("Rights for Robots");
		item.setStatus(ItemStatus.DRAFT);
		return item;
	}

	@Test
	void savesAndReadsBackABookAgainstARealPublisher() {
		Publisher publisher = publisherRepository.save(
				new Publisher(null, "RTLG-CI", "Routledge", null, null, RecordStatus.ACTIVE, null, null));

		CatalogueItem saved = catalogueItemRepository.save(newItem(publisher.getId(), List.of("col_law2024")));
		CatalogueItem found = catalogueItemRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getPublisherId()).isEqualTo(publisher.getId());
		assertThat(found.getTitle()).isEqualTo("Rights for Robots");
	}

	@Test
	void rejectsABookAgainstAPublisherThatDoesNotExist() {
		assertThatThrownBy(() -> catalogueItemRepository.save(newItem("does-not-exist", List.of("col_law2024"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allowsABookWithNoCollections() {
		Publisher publisher = publisherRepository.save(
				new Publisher(null, "NOCOL", "No Collections Publisher", null, null, RecordStatus.ACTIVE, null, null));

		CatalogueItem saved = catalogueItemRepository.save(newItem(publisher.getId(), List.of()));

		assertThat(saved.getId()).isNotNull();
	}

	@Test
	void findByContentStateFindsOnlyQueuedItems() {
		Publisher publisher = publisherRepository.save(
				new Publisher(null, "QUEUED-CI", "Queued Test Publisher", null, null, RecordStatus.ACTIVE, null, null));

		CatalogueItem none = newItem(publisher.getId(), List.of());
		none.setContentState(ContentState.NONE);
		CatalogueItem queued = newItem(publisher.getId(), List.of());
		queued.setContentState(ContentState.QUEUED);
		CatalogueItem ready = newItem(publisher.getId(), List.of());
		ready.setContentState(ContentState.READY);
		catalogueItemRepository.saveAll(List.of(none, queued, ready));

		List<CatalogueItem> found = catalogueItemRepository.findByContentState(ContentState.QUEUED);

		assertThat(found).extracting(CatalogueItem::getId).containsExactly(queued.getId());
	}

	@Test
	void findByContentStateInAndUpdatedAtBeforeFindsOnlyStaleQueuedOrProcessing() {
		Publisher publisher = publisherRepository
				.save(new Publisher(null, "STALE-CI", "Stale Test Publisher", null, null, RecordStatus.ACTIVE, null,
						null));
		Instant cutoff = Instant.parse("2026-08-26T10:00:00Z");
		Instant stale = cutoff.minusSeconds(60);
		Instant fresh = cutoff.plusSeconds(60);

		CatalogueItem staleQueued = newItem(publisher.getId(), List.of());
		staleQueued.setContentState(ContentState.QUEUED);
		staleQueued.setUpdatedAt(stale);
		CatalogueItem staleProcessing = newItem(publisher.getId(), List.of());
		staleProcessing.setContentState(ContentState.PROCESSING);
		staleProcessing.setUpdatedAt(stale);
		CatalogueItem freshQueued = newItem(publisher.getId(), List.of());
		freshQueued.setContentState(ContentState.QUEUED);
		freshQueued.setUpdatedAt(fresh);
		CatalogueItem staleReady = newItem(publisher.getId(), List.of());
		staleReady.setContentState(ContentState.READY);
		staleReady.setUpdatedAt(stale);
		catalogueItemRepository.saveAll(List.of(staleQueued, staleProcessing, freshQueued, staleReady));

		List<CatalogueItem> found = catalogueItemRepository
				.findByContentStateInAndUpdatedAtBefore(List.of(ContentState.QUEUED, ContentState.PROCESSING), cutoff);

		assertThat(found).extracting(CatalogueItem::getId).containsExactlyInAnyOrder(staleQueued.getId(),
				staleProcessing.getId());
	}

}
