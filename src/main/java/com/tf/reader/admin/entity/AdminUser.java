package com.tf.reader.admin.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "adminUsers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminUser {

	@Id
	private String id;

	@Indexed(unique = true)
	private String email;

	private String name;

	@JsonIgnore
	private String passwordHash;

	private AdminRole role;

	// Scopes the admin to one publisher/institution; null for a SUPER_ADMIN.
	private String publisherId;
	private String institutionId;

	private AdminStatus status;
	private Instant lastLoginAt;

}
