package com.tf.reader.auth.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderUserRepository;

/**
 * Seeds the four demo memberships {@code MockUserRepository} used to hold as a hardcoded map, so
 * local sign-in (samlmock.dev, or the local mock SAML/OIDC providers) resolves the same
 * identities it always has, now from the real {@code readerUsers} collection.
 *
 * <p>Gated exactly like {@code DemoDataSeeder} and {@code LoanSeedRunner} - local profile plus
 * {@code tnf.seed.enabled=true}. Insert-missing-only per row, like {@code LoanDevDataSeeder}: a
 * restart never duplicates or overwrites a row a developer has edited by hand.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "tnf.seed", name = "enabled", havingValue = "true")
public class ReaderUserDevDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ReaderUserDevDataSeeder.class);

	private final ReaderUserRepository readerUsers;

	public ReaderUserDevDataSeeder(ReaderUserRepository readerUsers) {
		this.readerUsers = readerUsers;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<ReaderUser> demoUsers = List.of(
				user("usr_6712ab", "john.doe@example.com", "inst_7f3",
						List.of("MEMBER"), List.of("col_medicine")),
				user("usr_8c14de", "john.doe@example.com", "inst_ucl",
						List.of("MEMBER"), List.of("col_engineering")),
				user("usr_3f81ab", "john.doe@example.com", "inst_leeds",
						List.of("MEMBER"), List.of("col_open")),
				user("usr_b920fe", "jane.roe@example.com", "inst_7f3",
						List.of("MEMBER", "ADMIN"), List.of("col_medicine", "col_engineering")));

		int inserted = 0;
		for (ReaderUser demoUser : demoUsers) {
			if (!readerUsers.existsById(demoUser.getId())) {
				readerUsers.save(demoUser);
				inserted++;
			}
		}
		if (inserted > 0) {
			log.info("Seeded {} demo reader user(s)", inserted);
		}
	}

	private static ReaderUser user(String userId, String email, String institutionId,
			List<String> roles, List<String> collections) {
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
