package com.tf.reader.auth.saml.mock.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A part of the local mock SAML identity provider: a component that exists <b>only</b> when
 * {@code saml-mock.enabled} is explicitly true.
 *
 * <p>Mirrors {@code MockOidcComponent} exactly, for the same reason: a mock identity provider is
 * a machine for minting identities for arbitrary users, so "switched off" has to mean the beans
 * and the endpoint <em>do not exist</em>, not that they exist and nobody calls them. Carrying the
 * condition on each class means it travels with the code rather than depending on a package
 * exclusion nobody remembers to update.
 *
 * <p>{@link com.tf.reader.auth.saml.mock.controller.SamlMockController} is the one exception in
 * form only: it needs {@code @RestController} for Spring MVC to detect it as a handler, and
 * carries the same {@code @ConditionalOnProperty} directly.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@ConditionalOnProperty(prefix = "saml-mock", name = "enabled", havingValue = "true")
public @interface SamlMockComponent {
}
