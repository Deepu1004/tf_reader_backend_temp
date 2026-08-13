package com.tf.reader.catalogue.entity;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "entitlements")
@CompoundIndexes({
		@CompoundIndex(name = "institution_status", def = "{'institutionId': 1, 'status': 1}"),
		@CompoundIndex(name = "institution_scope", def = "{'institutionId': 1, 'scopeType': 1, 'scopeId': 1}", unique = true),
		@CompoundIndex(name = "validTo_idx", def = "{'validTo': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Entitlement {

	@Id
	private String id;

	private String institutionId;
	private ScopeType scopeType;
	private String scopeId;

	// null means unlimited copies.
	private Integer copies;

	private Integer loanPeriodDays;
	private LocalDate validFrom;
	private LocalDate validTo;
	private EntitlementStatus status;
	private long version;
	private Instant createdAt;
	private Instant updatedAt;

}
