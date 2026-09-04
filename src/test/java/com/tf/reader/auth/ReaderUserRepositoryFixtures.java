package com.tf.reader.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderUserRepository;

/**
 * The same four memberships as {@link AuthTestUsers}, stubbed onto a mocked
 * {@link ReaderUserRepository} instead of saved to a real one. The mapper/service tests that use
 * this ({@code SamlUserMapperTest}, {@code OidcUserMapperTest}, {@code SamlAuthenticationServiceTest})
 * are deliberately Spring-context-free - see their own docstrings - and a mocked repository is
 * what keeps them that way, mirroring how {@code BorrowServiceTest} mocks {@code LoanRepository}
 * to test service logic rather than a query.
 */
public final class ReaderUserRepositoryFixtures {

	private ReaderUserRepositoryFixtures() {
	}

	public static ReaderUserRepository demoUsers() {
		ReaderUserRepository repository = mock(ReaderUserRepository.class);
		stub(repository, AuthTestUsers.JOHN_AT_IMPERIAL, "john.doe@example.com", "inst_7f3",
				List.of("MEMBER"), List.of("col_medicine"));
		stub(repository, AuthTestUsers.JOHN_AT_UCL, "john.doe@example.com", "inst_ucl",
				List.of("MEMBER"), List.of("col_engineering"));
		stub(repository, AuthTestUsers.JOHN_AT_LEEDS, "john.doe@example.com", "inst_leeds",
				List.of("MEMBER"), List.of("col_open"));
		stub(repository, AuthTestUsers.JANE_AT_IMPERIAL, "jane.roe@example.com", "inst_7f3",
				List.of("MEMBER", "ADMIN"), List.of("col_medicine", "col_engineering"));
		return repository;
	}

	private static void stub(ReaderUserRepository repository, String userId, String email,
			String institutionId, List<String> roles, List<String> collections) {
		ReaderUser user = ReaderUser.builder()
				.id(userId)
				.email(email)
				.type(UserType.INSTITUTION)
				.institutionId(institutionId)
				.roles(roles)
				.collections(collections)
				.build();
		when(repository.findByEmailAndInstitutionId(email, institutionId)).thenReturn(Optional.of(user));
	}
}
