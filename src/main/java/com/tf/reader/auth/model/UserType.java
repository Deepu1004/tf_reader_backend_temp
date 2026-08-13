package com.tf.reader.auth.model;

/**
 * The persona a signed-in user has. Fixed values, API Reference section 3.
 *
 * <p>An INSTITUTION user always carries an institutionId; an INDIVIDUAL never does.
 */
public enum UserType {

	INSTITUTION,
	INDIVIDUAL
}
