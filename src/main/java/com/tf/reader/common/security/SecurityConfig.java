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
 * HTTP security. Chains run most specific to least and the last denies everything, so a new endpoint
 * is unreachable until deliberately placed under a chain.
 *
 * <p>Each surface gets its own resource server and decoder, so audience separation is enforced by the
 * filter chain rather than by anything a controller has to remember.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	static final String LOGIN_PATH = "/api/admin/v1/auth/login";
	static final String REFRESH_PATH = "/api/admin/v1/auth/refresh";
	static final String LOGOUT_PATH = "/api/admin/v1/auth/logout";

	private final ApiErrorAuthenticationEntryPoint authenticationEntryPoint;
	private final ApiErrorAccessDeniedHandler accessDeniedHandler;

	public SecurityConfig(ApiErrorAuthenticationEntryPoint authenticationEntryPoint,
			ApiErrorAccessDeniedHandler accessDeniedHandler) {
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Login, refresh and logout, the only three public admin endpoints. All three carry their
	 * credential in the request body, so no resource server is attached: a stale {@code Authorization}
	 * header must not stop a caller logging in, refreshing or logging out.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain publicAdminAuthFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(LOGIN_PATH, REFRESH_PATH, LOGOUT_PATH)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, LOGIN_PATH, REFRESH_PATH, LOGOUT_PATH).permitAll()
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

	/** No app endpoints exist yet; the chain keeps the surface closed and bound to its own audience. */
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

	/** Dev profile only; elsewhere these paths fall through to the deny-all chain. */
	@Bean
	@Order(5)
	@Profile("dev")
	SecurityFilterChain apiDocsFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html",
				"/swagger-ui/**")
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
		return stateless(http).build();
	}

	/** Everything not matched above is denied. ERROR dispatches pass so a genuine 404 still renders. */
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
