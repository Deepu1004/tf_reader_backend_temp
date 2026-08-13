package com.tf.reader.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.tf.reader.admin.security.AdminJwtAuthenticationConverter;

import jakarta.servlet.DispatcherType;

/**
 * HTTP security.
 *
 * <p>The chains are ordered from most specific to least, and the last one denies everything. A new
 * endpoint is therefore unreachable until it is deliberately placed under a chain, which keeps the
 * default posture closed rather than open.
 *
 * <p>Each API surface gets its own resource server with its own decoder, so audience separation is
 * enforced by the filter chain rather than by anything a controller has to remember to do.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	static final String LOGIN_PATH = "/api/admin/v1/auth/login";
	static final String REFRESH_PATH = "/api/admin/v1/auth/refresh";

	private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
	private final ProblemAccessDeniedHandler accessDeniedHandler;

	public SecurityConfig(ProblemAuthenticationEntryPoint authenticationEntryPoint,
			ProblemAccessDeniedHandler accessDeniedHandler) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Login and refresh, the only two public admin endpoints.
	 *
	 * <p>Deliberately has no resource server attached. Both endpoints carry their credential in the
	 * request body, and if a bearer token were parsed here a stale one left in an
	 * {@code Authorization} header would fail the request instead of letting the caller log in or
	 * refresh.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain publicAdminAuthFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(LOGIN_PATH, REFRESH_PATH)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, LOGIN_PATH, REFRESH_PATH).permitAll()
						.anyRequest().denyAll());
		return stateless(http).build();
	}

	/** Liveness/readiness only. Every other actuator endpoint falls through to the deny-all chain. */
	@Bean
	@Order(2)
	SecurityFilterChain actuatorHealthFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/actuator/health", "/actuator/health/**")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
		return stateless(http).build();
	}

	/** Admin API. Requires a valid, session-backed {@code tf-admin} access token. */
	@Bean
	@Order(3)
	SecurityFilterChain adminApiFilterChain(HttpSecurity http,
			@Qualifier(JwtConfig.ADMIN_ACCESS_TOKEN_DECODER) JwtDecoder adminAccessTokenDecoder) throws Exception {

		http.securityMatcher("/api/admin/**")
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(this.authenticationEntryPoint)
						.accessDeniedHandler(this.accessDeniedHandler)
						.jwt(jwt -> jwt
								.decoder(adminAccessTokenDecoder)
								.jwtAuthenticationConverter(new AdminJwtAuthenticationConverter())));
		return stateless(http).build();
	}

	/**
	 * Reader app API. Requires a {@code tf-app} access token.
	 *
	 * <p>No app endpoints exist yet, but the chain is in place so the surface is closed and bound to
	 * its own audience from the start: an admin or refresh token presented here is rejected during
	 * decoding, before routing.
	 */
	@Bean
	@Order(4)
	SecurityFilterChain appApiFilterChain(HttpSecurity http,
			@Qualifier(JwtConfig.APP_ACCESS_TOKEN_DECODER) JwtDecoder appAccessTokenDecoder) throws Exception {

		http.securityMatcher("/api/app/**")
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(this.authenticationEntryPoint)
						.accessDeniedHandler(this.accessDeniedHandler)
						.jwt(jwt -> jwt.decoder(appAccessTokenDecoder)));
		return stateless(http).build();
	}

	/**
	 * API documentation, dev profile only.
	 *
	 * <p>Absent in every other profile, so the deny-all chain below covers these paths there even
	 * though springdoc is also switched off by configuration.
	 */
	@Bean
	@Order(5)
	@Profile("dev")
	SecurityFilterChain apiDocsFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html",
				"/swagger-ui/**")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
		return stateless(http).build();
	}

	/**
	 * Everything not matched above is denied.
	 *
	 * <p>ERROR dispatches are permitted so that a genuine 404 or 500 can still be rendered; that
	 * dispatch type cannot be triggered directly by a client.
	 */
	@Bean
	@Order(100)
	SecurityFilterChain denyAllFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.anyRequest().denyAll());
		return stateless(http).build();
	}

	/** Stateless JSON API: no sessions, no CSRF token exchange, no browser login flows. */
	private HttpSecurity stateless(HttpSecurity http) throws Exception {
		return http.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.disable())
				.anonymous(Customizer.withDefaults())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(this.authenticationEntryPoint)
						.accessDeniedHandler(this.accessDeniedHandler));
	}

}
