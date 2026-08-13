package com.tf.reader.admin.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.common.security.TokenClaims;

/**
 * Maps the {@code role} claim onto a single Spring Security authority, {@code ROLE_<AdminRole>}.
 *
 * <p>Role and scope stay separate: only the role becomes an authority. Scope is evaluated per
 * target by {@link AdminScopeAuthorizer}, which avoids one authority per publisher or institution.
 *
 * <p>The claim is already constrained to a valid {@link AdminRole} by the decoder's validator
 * chain, so an unparseable role never reaches this converter.
 */
public final class AdminJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	@Override
	public JwtAuthenticationToken convert(Jwt jwt) {
		return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
	}

	private static List<GrantedAuthority> authorities(Jwt jwt) {
		AdminRole role = AdminRoles.parse(jwt.getClaimAsString(TokenClaims.ROLE));
		if (role == null) {
			return List.of();
		}
		return List.of(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role.name()));
	}

}
