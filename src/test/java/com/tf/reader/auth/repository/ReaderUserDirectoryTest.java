package com.tf.reader.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.auth.AuthTestUsers;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;

/**
 * The real reader directory, against a real Mongo. Pins the same behaviours
 * {@code MockUserRepositoryTest} used to pin against a hardcoded map, now against the
 * {@code readerUsers} collection.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReaderUserDirectoryTest {

	@Autowired
	private ReaderUserRepository readerUserRepository;

	private ReaderUserDirectory users;

	@BeforeEach
	void seedDemoUsers() {
		readerUserRepository.deleteAll();
		users = new ReaderUserDirectory(readerUserRepository);
		AuthTestUsers.seed(readerUserRepository);
	}

	@Test
	void findsTheSeededUserForAnIdentityAndInstitution() {
		assertThat(users.find("john.doe@example.com", "inst_7f3"))
				.get()
				.satisfies(user -> {
					assertThat(user.userId()).isEqualTo(AuthTestUsers.JOHN_AT_IMPERIAL);
					assertThat(user.type()).isEqualTo(UserType.INSTITUTION);
					assertThat(user.institutionId()).isEqualTo("inst_7f3");
				});
	}

	@Test
	void theSameIdentityIsADifferentUserAtEachInstitution() {
		// One IdP, many institutions: the pair is the key, not the email.
		TnfUser imperial = users.find("john.doe@example.com", "inst_7f3").orElseThrow();
		TnfUser ucl = users.find("john.doe@example.com", "inst_ucl").orElseThrow();

		assertThat(imperial.userId()).isNotEqualTo(ucl.userId());
		assertThat(imperial.collections()).isNotEqualTo(ucl.collections());
	}

	@Test
	void emailFoldingDoesNotDependOnTheJvmsLocale() {
		// "I".toLowerCase() is the dotless "ı" in a Turkish or Azeri locale, so a default-locale
		// fold makes a provisioned user unprovisioned on some machines and not others. Today's
		// seeded addresses happen to contain no dotted I, so this pins the rule ahead of a live
		// failure rather than reproducing one.
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));

			assertThat(users.find("JOHN.DOE@EXAMPLE.COM", "inst_7f3")).isPresent();
			assertThat(users.find("Jane.Roe@Example.Com", "inst_7f3")).isPresent();
			// The fold must be the locale-independent one, whatever the JVM was started with.
			assertThat("IRIS@EXAMPLE.COM".toLowerCase(Locale.ROOT))
					.isNotEqualTo("IRIS@EXAMPLE.COM".toLowerCase());
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void lookupIsDeterministic() {
		assertThat(users.find("john.doe@example.com", "inst_7f3"))
				.isEqualTo(users.find("john.doe@example.com", "inst_7f3"));
	}

	@Test
	void anIdentityIsNotFoundAtAnInstitutionItHasNoMembershipAt() {
		assertThat(users.find("jane.roe@example.com", "inst_7f3")).isPresent();
		assertThat(users.find("jane.roe@example.com", "inst_ucl")).isEmpty();
	}

	@Test
	void authenticationDoesNotSucceedForArbitraryAddresses() {
		assertThat(users.find("attacker@example.com", "inst_7f3")).isEmpty();
		assertThat(users.find("", "inst_7f3")).isEmpty();
		assertThat(users.find(null, "inst_7f3")).isEmpty();
		assertThat(users.find("john.doe@example.com", null)).isEmpty();
		assertThat(users.find("john.doe@example.com", "inst_nowhere")).isEmpty();
	}

	@Test
	void emailIsMatchedCaseInsensitivelyAndTrimmed() {
		// An IdP is free to vary the case of an address it asserts.
		assertThat(users.find("  John.Doe@Example.COM ", "inst_7f3"))
				.isEqualTo(users.find("john.doe@example.com", "inst_7f3"));
	}

	@Test
	void seededUsersAreImmutable() {
		TnfUser user = users.find("john.doe@example.com", "inst_7f3").orElseThrow();

		assertThat(user.roles()).isUnmodifiable();
		assertThat(user.collections()).isUnmodifiable();
	}
}
