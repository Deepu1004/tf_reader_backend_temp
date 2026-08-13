package com.tf.reader.catalogue.entity;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Shelf {

	private String id;
	private String title;
	private int order;
	private List<String> itemIds;

}
