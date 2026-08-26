package com.tf.reader.catalogue.opds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationDocument;
import com.tf.reader.catalogue.opds.service.OpdsPublicFeedService;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;

/**
 * The no-sign-in publication detail endpoint (Workstream 9): metadata for any
 * {@code PUBLISHED}/{@code READY} book regardless of entitlement, and a {@code 404} for
 * everything else - unknown, archived, still a draft, or published but not yet processed.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class OpdsPublicFeedServiceIT extends ContainerisedInfrastructure {

	@Autowired private OpdsPublicFeedService publicFeedService;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private CatalogueItemRepository catalogueItemRepository;

	private Publisher newPublisher(String code) {
		return publisherRepository.save(
				new Publisher(null, code, code + " Press", null, null, RecordStatus.ACTIVE, null, null));
	}

	private CatalogueItem newItem(String publisherId, AccessTier accessTier, ItemStatus status,
			ContentState contentState) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setCollectionIds(List.of());
		item.setTitle("Publication Detail Fixture");
		item.setIsbn("9780367211745");
		item.setAccessTier(accessTier);
		item.setStatus(status);
		item.setContentState(contentState);
		item.setContentType(ContentType.EPUB);
		item.setPublishedAt(LocalDate.of(2026, 1, 1));
		item.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return catalogueItemRepository.save(item);
	}

	@Test
	void openAccessItemCarriesTheRealAcquisitionLink() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-OA-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED,
				ContentState.READY);

		OpdsPublicationDocument document = publicFeedService.publicationDocument(item.getId());

		assertThat(document.context()).isEqualTo("https://readium.org/webpub-manifest/context.jsonld");
		assertThat(document.links()).extracting("rel")
				.contains("http://opds-spec.org/acquisition/open-access");
	}

	@Test
	void eliteItemCarriesASubscribeLinkMarkedUnavailable() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-ELITE-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.ELITE, ItemStatus.PUBLISHED, ContentState.READY);

		OpdsPublicationDocument document = publicFeedService.publicationDocument(item.getId());

		assertThat(document.links()).extracting("rel")
				.contains("http://opds-spec.org/acquisition/subscribe");
	}

	@Test
	void unknownItemIsNotFound() {
		assertThatThrownBy(() -> publicFeedService.publicationDocument("item_does_not_exist"))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void archivedItemIsNotFound() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-ARCHIVED-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.ARCHIVED,
				ContentState.READY);

		assertThatThrownBy(() -> publicFeedService.publicationDocument(item.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void draftItemIsNotFound() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-DRAFT-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.DRAFT,
				ContentState.READY);

		assertThatThrownBy(() -> publicFeedService.publicationDocument(item.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void publishedButNotYetReadyItemIsNotFound() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-QUEUED-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED,
				ContentState.QUEUED);

		assertThatThrownBy(() -> publicFeedService.publicationDocument(item.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
