package com.tf.reader.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.admin.config.DevAdminSeeder;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.repository.AdminUserRepository;

/**
 * The seeder runs once at startup under the dev profile; these assertions inspect what it left
 * behind and then re-run it to confirm it does not duplicate.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DevAdminSeederTest {

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private DevAdminSeeder devAdminSeeder;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${tf.admin.seed.password}")
	private String seedPassword;

	@Test
	void seedsOneAdminPerRole() {
		assertThat(byEmail("super.admin@tf-reader.local"))
				.satisfies(admin -> {
					assertThat(admin.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
					assertThat(admin.getStatus()).isEqualTo(AdminStatus.ACTIVE);
					assertThat(admin.getPublisherId()).isNull();
					assertThat(admin.getInstitutionId()).isNull();
				});

		assertThat(byEmail("publisher.admin@tf-reader.local"))
				.satisfies(admin -> {
					assertThat(admin.getRole()).isEqualTo(AdminRole.PUBLISHER_ADMIN);
					assertThat(admin.getPublisherId()).isEqualTo("dev-publisher");
					assertThat(admin.getInstitutionId()).isNull();
				});

		assertThat(byEmail("institution.admin@tf-reader.local"))
				.satisfies(admin -> {
					assertThat(admin.getRole()).isEqualTo(AdminRole.INSTITUTION_ADMIN);
					assertThat(admin.getInstitutionId()).isEqualTo("dev-institution");
					assertThat(admin.getPublisherId()).isNull();
				});
	}

	@Test
	void storesBcryptHashesRatherThanPlaintext() {
		AdminUser admin = byEmail("super.admin@tf-reader.local");

		assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo(this.seedPassword);
		assertThat(this.passwordEncoder.matches(this.seedPassword, admin.getPasswordHash())).isTrue();
	}

	@Test
	void doesNotDuplicateOrOverwriteOnRepeatedRuns() {
		long countBefore = this.adminUserRepository.count();
		String hashBefore = byEmail("super.admin@tf-reader.local").getPasswordHash();
		String idBefore = byEmail("super.admin@tf-reader.local").getId();

		ApplicationArguments noArguments = new DefaultApplicationArguments();
		this.devAdminSeeder.run(noArguments);
		this.devAdminSeeder.run(noArguments);

		assertThat(this.adminUserRepository.count()).isEqualTo(countBefore);
		assertThat(byEmail("super.admin@tf-reader.local").getId()).isEqualTo(idBefore);
		assertThat(byEmail("super.admin@tf-reader.local").getPasswordHash()).isEqualTo(hashBefore);
	}

	private AdminUser byEmail(String email) {
		return this.adminUserRepository.findByEmail(email).orElseThrow();
	}

}
