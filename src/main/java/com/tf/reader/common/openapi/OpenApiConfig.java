package com.tf.reader.common.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API documentation, restricted to the dev profile.
 *
 * <p>Two independent switches keep it off elsewhere: springdoc is disabled by configuration in
 * {@code application.yml}, and the filter chain that exposes its paths only exists under the dev
 * profile. Nothing here relaxes security for the documented endpoints themselves.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class OpenApiConfig {

	static final String ADMIN_BEARER_SCHEME = "adminBearerAuth";

	@Bean
	OpenAPI readerOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("T&F Reader admin API")
						.version("v1")
						.description("Admin authentication and RBAC. Access tokens carry aud=tf-admin; "
								+ "refresh tokens carry aud=tf-refresh and are accepted only by the refresh "
								+ "endpoint."))
				.components(new Components().addSecuritySchemes(ADMIN_BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Admin access token (aud=tf-admin) issued by the login endpoint.")));
	}

}
