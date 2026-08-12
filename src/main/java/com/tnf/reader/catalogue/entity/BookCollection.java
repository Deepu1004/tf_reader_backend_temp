package com.tnf.reader.catalogue.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "collections")
@CompoundIndex(name = "publisher_code", def = "{'publisherId': 1, 'code': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookCollection {

	@Id
	private String id;

	private String publisherId;
	private String code;
	private String name;
	private String description;

}
