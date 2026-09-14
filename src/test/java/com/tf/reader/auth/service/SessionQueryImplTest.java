package com.tf.reader.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.tf.reader.auth.api.SessionView;
import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.security.CurrentUserAuthenticationToken;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * The seam every other module calls to find out who is asking, without ever seeing a
 * {@code CurrentUser}. {@code library.CurrentReaderResolverTest} exercises the same guard through
 * that module's own {@link com.tf.reader.library.support.ReaderIdentity} shape.
 */
class SessionQueryImplTest {

	private final SessionQueryImpl session = new SessionQueryImpl();

	@Test
	void mapsAVerifiedPrincipalToASessionView() {
		SessionView view = session.of(authenticated(
				new CurrentUser("usr_6712ab", UserType.INSTITUTION, "inst_7f3",
						List.of("MEMBER"), List.of("col_medicine"))));

		assertThat(view.userId()).isEqualTo("usr_6712ab");
		assertThat(view.institutionId()).isEqualTo("inst_7f3");
		assertThat(view.roles()).containsExactly("MEMBER");
	}

	@Test
	void refusesAnythingThatIsNotAVerifiedCurrentUser() {
		assertThatThrownBy(() -> session.of(null))
				.isInstanceOf(ApiException.class)
				.satisfies(thrown -> assertThat(((ApiException) thrown).getCode())
						.isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

	private static CurrentUserAuthenticationToken authenticated(CurrentUser reader) {
		return new CurrentUserAuthenticationToken(reader, null,
				List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}
}
