package com.tf.reader.auth;

import java.util.List;

import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderUserRepository;

/**
 * The four (email, institutionId) memberships the auth test suite signs in against, saved as real
 * documents in the real {@code readerUsers} collection - the same four {@code MockUserRepository}
 * used to hold as a hardcoded map. Used by any Testcontainers-backed test that goes through the
 * real {@code SamlUserMapper}/{@code OidcUserMapper} beans, alongside {@link AuthTestInstitutions}.
 */
public final class AuthTestUsers {

	public static final String JOHN_AT_IMPERIAL = "usr_6712ab";
	public static final String JOHN_AT_UCL = "usr_8c14de";
	public static final String JOHN_AT_LEEDS = "usr_3f81ab";
	public static final String JANE_AT_IMPERIAL = "usr_b920fe";

	private AuthTestUsers() {
	}

	public static void seed(ReaderUserRepository readerUsers) {
		readerUsers.save(user(JOHN_AT_IMPERIAL, "john.doe@example.com", AuthTestInstitutions.IMPERIAL,
				List.of("MEMBER"), List.of("col_medicine")));
		readerUsers.save(user(JOHN_AT_UCL, "john.doe@example.com", AuthTestInstitutions.UCL,
				List.of("MEMBER"), List.of("col_engineering")));
		readerUsers.save(user(JOHN_AT_LEEDS, "john.doe@example.com", AuthTestInstitutions.LEEDS,
				List.of("MEMBER"), List.of("col_open")));
		readerUsers.save(user(JANE_AT_IMPERIAL, "jane.roe@example.com", AuthTestInstitutions.IMPERIAL,
				List.of("MEMBER", "ADMIN"), List.of("col_medicine", "col_engineering")));
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
