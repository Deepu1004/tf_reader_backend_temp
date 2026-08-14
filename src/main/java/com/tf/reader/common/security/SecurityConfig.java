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

	/**
	 * The app-side prefixes, taken from the API contract rather than invented. Everything under them
	 * needs a {@code tf-app} token unless one of the public matchers below claims it first.
	 */
	static final String APP_API_PATHS = "/api/v1/**";
	static final String APP_OPDS_PATHS = "/opds/v1/**";

	/**
	 * The app paths the contract marks {@code security: []}. Public institution discovery is how a
	 * reader chooses where to sign in, and the public OPDS feeds are open-access browsing, so both have
	 * to work before anyone holds a token at all.
	 */
	static final String PUBLIC_INSTITUTIONS_PATH = "/api/v1/institutions";

	/** One segment only, so a later {@code /{id}/something-private} does not inherit public access. */
	static final String PUBLIC_INSTITUTION_PATH = "/api/v1/institutions/*";

	static final String PUBLIC_OPDS_PATHS = "/opds/v1/public/**";

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

	/**
	 * The app paths that carry no token: public institution discovery and the open-access feeds.
	 *
	 * <p>Ordered ahead of the {@code tf-app} chain because only the first chain whose matcher matches
	 * ever runs, and these paths sit underneath its prefixes. Without this chain, binding the app
	 * surface to {@code tf-app} would make team1's institution picker and anonymous open-access
	 * browsing require a token the caller cannot have yet.
	 *
	 * <p>No resource server is attached, for the same reason as the admin auth chain: a stale token
	 * left in an {@code Authorization} header must not break a request that needs no token, so a reader
	 * whose app token has expired can still browse open access.
	 */
	@Bean
	@Order(3)
	SecurityFilterChain publicAppFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(PUBLIC_INSTITUTIONS_PATH, PUBLIC_INSTITUTION_PATH, PUBLIC_OPDS_PATHS)
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.GET, PUBLIC_INSTITUTIONS_PATH, PUBLIC_INSTITUTION_PATH,
								PUBLIC_OPDS_PATHS)
						.permitAll()
						.anyRequest().denyAll());
		return stateless(http).build();
	}

	/** Admin API. Requires a valid, session-backed {@code tf-admin} access token. */
	@Bean
	@Order(4)
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
	 * Reader app API: the institution-scoped OPDS feeds and the catalogue batch endpoint.
	 *
	 * <p>Most of these are not written yet. The chain still binds the surface to its own audience now,
	 * so an admin or refresh token presented here is rejected during decoding, before routing, and an
	 * endpoint another team adds later inherits that without anyone having to remember.
	 */
	@Bean
	@Order(5)
	SecurityFilterChain appApiFilterChain(HttpSecurity http,
			@Qualifier(JwtConfig.APP_ACCESS_TOKEN_DECODER) JwtDecoder appAccessTokenDecoder) throws Exception {

		http.securityMatcher(APP_API_PATHS, APP_OPDS_PATHS)
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
	@Order(6)
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
