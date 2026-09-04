package com.tf.reader.auth.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderUserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds the demo reader memberships {@code MockUserRepository} used to hold as a hardcoded map,
 * so local sign-in (samlmock.dev, or the local mock SAML/OIDC providers) resolves the same
 * identities it always has, now from the real {@code readerUsers} collection.
 *
 * <p>Shares {@code flambeau-seed.json}'s {@code users} array with the sibling seeders in
 * loan/hold/reading/library — one file, one array per module. Same safety rails as those: local
 * profile, the shared {@code tnf.seed.enabled} flag, insert-missing-only, never a delete or
 * overwrite of a row a developer has edited by hand.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "tnf.seed", name = "enabled", havingValue = "true")
public class ReaderUserDevDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ReaderUserDevDataSeeder.class);
	private static final String DATASET_PATH = "seed/flambeau-seed.json";

	private final ReaderUserRepository readerUsers;
	private final ObjectMapper mapper;

	public ReaderUserDevDataSeeder(ReaderUserRepository readerUsers, ObjectMapper mapper) {
		this.readerUsers = readerUsers;
		this.mapper = mapper;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		List<SeedUser> seeds;
		try (InputStream in = new ClassPathResource(DATASET_PATH).getInputStream()) {
			JsonNode root = mapper.readTree(in);
			seeds = mapper.convertValue(root.get("users"),
					mapper.getTypeFactory().constructCollectionType(List.class, SeedUser.class));
		}

		int inserted = 0;
		for (SeedUser seed : seeds) {
			if (readerUsers.existsById(seed.userId())) {
				continue;
			}
			readerUsers.save(seed.toReaderUser());
			inserted++;
		}
		log.info("flambeau reader user seed: {} inserted, {} already present", inserted, seeds.size() - inserted);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SeedUser(
			String userId,
			String email,
			String institutionId,
			List<String> roles,
			List<String> collections) {

		ReaderUser toReaderUser() {
			return ReaderUser.builder()
					.id(userId)
					.email(email)
					.type(UserType.INSTITUTION)
					.institutionId(institutionId)
					.roles(roles)
					.collections(collections)
					.build();
		}
	}
}
