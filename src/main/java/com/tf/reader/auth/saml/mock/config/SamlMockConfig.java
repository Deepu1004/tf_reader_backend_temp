package com.tf.reader.auth.saml.mock.config;

import com.tf.reader.auth.saml.mock.controller.SamlMockController;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Switches the local mock SAML IdP on, or - by default - leaves it out of the application.
 *
 * <p>Mirrors {@code MockOidcConfig}: nothing under {@code saml-mock.enabled=false} exists, and
 * the one thing here that is not a {@code @SamlMockComponent} bean is the filter chain that opens
 * the mock's own path.
 */
@Configuration
@EnableConfigurationProperties(SamlMockProperties.class)
@ConditionalOnProperty(prefix = "saml-mock", name = "enabled", havingValue = "true")
public class SamlMockConfig {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(SamlMockConfig.class);

	public SamlMockConfig() {
		// Loud on purpose, for the same reason MockOidcConfig's own line is: this appearing in a
		// production log is an incident, and it should be greppable without knowing what to grep for.
		log.warn("MOCK SAML IDP IS ENABLED at {} - local development only, never a real environment",
				SamlMockController.SSO_PATH);
	}

	/**
	 * The mock's own path, reachable without a token or a session - a provider that demanded
	 * either before issuing an identity would be a circle.
	 */
	@Bean
	@Order(0)
	SecurityFilterChain samlMockFilterChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(SamlMockController.SSO_PATH)
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.build();
	}
}
