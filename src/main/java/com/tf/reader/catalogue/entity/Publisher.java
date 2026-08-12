package com.tf.reader.catalogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tf.reader.common.model.RecordStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "publishers")
@CompoundIndex(name = "status_name", def = "{'status': 1, 'name': 1}")
@Getter
@Setter
@NoArgsConstructor
public class Publisher {

	@Id
	private String id;

	@Indexed(unique = true)
	private String code;

	private String name;
	private String description;
	private String logoUrl;
	private RecordStatus status;
	private Instant createdAt;
	private Instant updatedAt;

	public Publisher(String id, String code, String name, String description, String logoUrl,
			RecordStatus status, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.code = normalize(code);
		this.name = name;
		this.description = description;
		this.logoUrl = logoUrl;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public void setCode(String code) {
		this.code = normalize(code);
	}

	private static String normalize(String code) {
		return code == null ? null : code.toUpperCase();
	}

}
