package com.tf.reader.auth.entity;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mongo document for an end reader: one row per (email, institutionId) membership - the real
 * thing {@code MockUserRepository} stood in for as a hardcoded map. One SAML or OIDC identity may
 * be a member of several institutions and is a different row, and a different user, in each.
 */
@Document(collection = "readerUsers")
@CompoundIndex(name = "email_institutionId_uk", def = "{'email': 1, 'institutionId': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReaderUser {

	/** Prefixed {@code usr_}. */
	@Id
	private String id;

	/** Always stored lower-cased, {@link java.util.Locale#ROOT}-folded - see {@code ReaderUserDirectory}. */
	private String email;

	private UserType type;

	/** Null for an INDIVIDUAL. */
	private String institutionId;

	private List<String> roles;

	private List<String> collections;

	public TnfUser toTnfUser() {
		return new TnfUser(id, type, institutionId, roles, collections);
	}
}
