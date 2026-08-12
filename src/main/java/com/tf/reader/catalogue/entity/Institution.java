package com.tf.reader.catalogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tf.reader.common.model.RecordStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "institutions")
@CompoundIndex(name = "status_name", def = "{'status': 1, 'name': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Institution {

	@Id
	private String id;

	@Indexed(unique = true)
	private String code;

	@TextIndexed
	private String name;

	private InstitutionType type;
	private String country;

	@TextIndexed
	private String city;

	private Branding branding;
	private SignIn signIn;
	private RecordStatus status;
	private long catalogueVersion;
	private Instant createdAt;
	private Instant updatedAt;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SignIn {

		private String method;
		private String idpHint;

	}

}
