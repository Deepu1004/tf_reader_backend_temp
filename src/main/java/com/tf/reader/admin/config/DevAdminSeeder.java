package com.tf.reader.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.repository.AdminUserRepository;

/**
 * Creates one admin per role for local development.
 *
 * <p>Gated twice: the dev profile, and {@code tf.admin.seed.enabled}, which is only ever set in
 * {@code application-dev.yml}. Uses the existing {@link AdminUser} document, and skips any admin
 * whose email is already present, so repeated startups do not duplicate or overwrite anything.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "tf.admin.seed.enabled", havingValue = "true")
public class DevAdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

	static final String SUPER_ADMIN_EMAIL = "super.admin@tf-reader.local";
	static final String PUBLISHER_ADMIN_EMAIL = "publisher.admin@tf-reader.local";
	static final String INSTITUTION_ADMIN_EMAIL = "institution.admin@tf-reader.local";

	private final AdminUserRepository adminUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final String seedPassword;
	private final String seedPublisherId;
	private final String seedInstitutionId;

	public DevAdminSeeder(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
			@Value("${tf.admin.seed.password}") String seedPassword,
			@Value("${tf.admin.seed.publisher-id:dev-publisher}") String seedPublisherId,
			@Value("${tf.admin.seed.institution-id:dev-institution}") String seedInstitutionId) {

		this.adminUserRepository = adminUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.seedPassword = seedPassword;
		this.seedPublisherId = seedPublisherId;
		this.seedInstitutionId = seedInstitutionId;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed(SUPER_ADMIN_EMAIL, "Dev Super Admin", AdminRole.SUPER_ADMIN, null, null);
		seed(PUBLISHER_ADMIN_EMAIL, "Dev Publisher Admin", AdminRole.PUBLISHER_ADMIN, this.seedPublisherId, null);
		seed(INSTITUTION_ADMIN_EMAIL, "Dev Institution Admin", AdminRole.INSTITUTION_ADMIN, null,
				this.seedInstitutionId);
	}

	private void seed(String email, String name, AdminRole role, String publisherId, String institutionId) {
		if (this.adminUserRepository.findByEmail(email).isPresent()) {
			return;
		}

		AdminUser adminUser = new AdminUser();
		adminUser.setEmail(email);
		adminUser.setName(name);
		adminUser.setPasswordHash(this.passwordEncoder.encode(this.seedPassword));
		adminUser.setRole(role);
		adminUser.setPublisherId(publisherId);
		adminUser.setInstitutionId(institutionId);
		adminUser.setStatus(AdminStatus.ACTIVE);

		this.adminUserRepository.save(adminUser);
		log.info("Seeded development admin {} with role {}", email, role);
	}

}
