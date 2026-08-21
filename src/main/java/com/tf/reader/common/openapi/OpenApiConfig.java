package com.tf.reader.common.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API documentation, restricted to the dev profile by two independent switches: springdoc is
 * disabled in {@code application.yml}, and the filter chain exposing its paths is dev-only.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class OpenApiConfig {

	/** The name the published API contract gives this scheme. */
	static final String ADMIN_BEARER_SCHEME = "adminToken";

	@Bean
	OpenAPI readerOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("T&F Reader admin API")
						.version("v1")
						.description("Admin authentication and RBAC. Access tokens are JWTs carrying "
								+ "aud=tf-admin; refresh tokens are opaque and are accepted only in the body of "
								+ "the refresh and logout endpoints, never as a bearer token."))
				.components(new Components().addSecuritySchemes(ADMIN_BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Admin access token (aud=tf-admin) issued by the login endpoint.")));
	}

}
