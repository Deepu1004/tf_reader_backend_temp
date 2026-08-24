package com.tf.reader.catalogue.opds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.FeedSettings;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.Shelf;
import com.tf.reader.catalogue.opds.dto.OpdsNavigationFeed;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.opds.service.OpdsFeedService;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.FeedSettingsRepository;
import com.tf.reader.catalogue.repository.InstitutionRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageQuery;

/**
 * Validates real, generated OPDS feeds against the schemas vendored at
 * {@code api-docs/opds/schema/} - the same relative path, {@code opds/schema/}, the Week 3
 * plan names for "the docs repo" ({@code api-docs/} being this repo's vendoring location for
 * docs-repo content, same as {@code wokay-api.yaml} itself). Covers "every response passing
 * the vendored OPDS schema check", including the empty-{@code all} navigation-fallback case,
 * since a code comment alone is not proof the shape is valid.
 *
 * <p>The schema files themselves are hand-derived from {@code api-docs/wokay-api.yaml}, not
 * the canonical copy from the docs repo (unreachable from this environment) - overwrite them
 * in place at {@code api-docs/opds/schema/} when that copy is available; this test does not
 * need to change.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class OpdsFeedSchemaValidationIT extends ContainerisedInfrastructure {

	@Autowired private OpdsFeedService feedService;
	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private CatalogueItemRepository catalogueItemRepository;
	@Autowired private FeedSettingsRepository feedSettingsRepository;
	@Autowired private tools.jackson.databind.ObjectMapper appJson;

	private static final JsonSchemaFactory SCHEMA_FACTORY =
			JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

	private Institution newInstitution(String code) {
		Institution institution = new Institution();
		institution.setCode(code);
		institution.setName(code + " Institution");
		institution.setType(InstitutionType.ACADEMIC);
		institution.setCountry("UK");
		institution.setStatus(RecordStatus.ACTIVE);
		institution.setCatalogueVersion(1L);
		institution.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return institutionRepository.save(institution);
	}

	private Publisher newPublisher(String code) {
		return publisherRepository.save(
				new Publisher(null, code, code + " Press", null, null, RecordStatus.ACTIVE, null, null));
	}

	private CatalogueItem newOpenAccessItem(String publisherId, String title) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setCollectionIds(List.of());
		item.setTitle(title);
		item.setAccessTier(AccessTier.OPEN_ACCESS);
		item.setStatus(ItemStatus.PUBLISHED);
		item.setContentState(ContentState.READY);
		item.setContentType(ContentType.EPUB);
		item.setPublishedAt(LocalDate.of(2026, 1, 1));
		item.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return catalogueItemRepository.save(item);
	}

	private void saveFeedSettings(String institutionId, Shelf shelf) {
		java.util.Map<String, Shelf> byId = new java.util.LinkedHashMap<>();
		byId.put(shelf.getId(), shelf);
		List<Shelf> shelves = new java.util.ArrayList<>();
		int order = 1;
		for (String id : List.of("shelf_1", "shelf_2", "shelf_3")) {
			shelves.add(byId.getOrDefault(id, new Shelf(id, "Empty", order, List.of())));
			order++;
		}
		feedSettingsRepository.save(
				new FeedSettings(null, institutionId, "Feed", 20, "publishedAt.desc", shelves, null, 0));
	}

	// api-docs/, not the test classpath: this is where the docs repo's own opds/schema/
	// files get vendored in this repo, same as wokay-api.yaml itself. Surefire's working
	// directory is the module basedir, so this path resolves the same under mvn and an IDE
	// run configured with the default working directory.
	private static final Path SCHEMA_DIR = Path.of("api-docs", "opds", "schema");

	private JsonSchema schema(String fileName) throws Exception {
		try (InputStream in = Files.newInputStream(SCHEMA_DIR.resolve(fileName))) {
			return SCHEMA_FACTORY.getSchema(in);
		}
	}

	private Set<ValidationMessage> validate(JsonSchema schema, Object dto) throws Exception {
		String json = appJson.writeValueAsString(dto);
		com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
		return schema.validate(node);
	}

	@Test
	void rootFeedWithACuratedShelfMatchesTheNavigationFeedSchema() throws Exception {
		Institution institution = newInstitution("OPDS-SCHEMA-ROOT");
		Publisher publisher = newPublisher("OPDS-SCHEMA-ROOT-PUB");
		CatalogueItem item = newOpenAccessItem(publisher.getId(), "Schema Checked Book");
		saveFeedSettings(institution.getId(), new Shelf("shelf_1", "New this term", 1, List.of(item.getId())));

		OpdsNavigationFeed feed = feedService.rootFeed(institution, new SubjectRef("user_1", institution.getId()));

		assertThat(validate(schema("opds-navigation-feed.schema.json"), feed)).isEmpty();
	}

	@Test
	void nonEmptyAllGroupMatchesThePublicationFeedSchema() throws Exception {
		Institution institution = newInstitution("OPDS-SCHEMA-ALL-OK");
		Publisher publisher = newPublisher("OPDS-SCHEMA-ALL-OK-PUB");
		newOpenAccessItem(publisher.getId(), "Schema Checked Open Book");

		OpdsPublicationFeed feed = feedService.groupFeed(institution, "all",
				new SubjectRef("user_1", institution.getId()), new PageQuery(0, 20), null, null, null);

		assertThat(feed.publications()).isNotNull();
		assertThat(validate(schema("opds-publication-feed.schema.json"), feed)).isEmpty();
	}

	// The specific case the review flagged: OPDS forbids an empty `publications` array, so the
	// zero-result "all" feed substitutes `navigation` instead - assert that shape against the
	// schema itself, not just against what the code comment claims it does.
	@Test
	void emptyAllGroupFallsBackToNavigationAndStillMatchesTheSchema() throws Exception {
		Institution institution = newInstitution("OPDS-SCHEMA-ALL-EMPTY");

		OpdsPublicationFeed feed = feedService.groupFeed(institution, "all",
				new SubjectRef("user_1", institution.getId()), new PageQuery(0, 20), null, null,
				AccessTier.SUBSCRIPTION);

		assertThat(feed.publications()).isNull();
		assertThat(validate(schema("opds-publication-feed.schema.json"), feed)).isEmpty();
	}
}
