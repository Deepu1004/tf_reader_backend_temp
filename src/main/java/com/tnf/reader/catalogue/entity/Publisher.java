package com.tnf.reader.catalogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tnf.reader.common.model.RecordStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "publishers")
@CompoundIndex(name = "status_name", def = "{'status': 1, 'name': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

}
