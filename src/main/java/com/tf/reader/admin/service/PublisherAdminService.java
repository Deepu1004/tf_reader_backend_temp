package com.tf.reader.admin.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.tf.reader.admin.dto.PublisherView;
import com.tf.reader.admin.dto.PublisherWrite;
import com.tf.reader.admin.dto.StatusChange;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageQuery;
import com.tf.reader.common.page.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * All write-side publisher operations for the admin console.
 *
 * <p>
 * The controller is HTTP-only. Every business rule lives here so a second
 * endpoint added later cannot bypass them.
 *
 * <p>
 * {@code itemCount} and {@code collectionCount} are derived on every read by
 * counting documents in {@code catalogueItems} and {@code collections} — they
 * are never stored on the publisher.
 */
@Service
@RequiredArgsConstructor
public class PublisherAdminService {

	private final PublisherRepository publisherRepository;
	private final CatalogueItemRepository catalogueItemRepository;
	private final BookCollectionRepository bookCollectionRepository;
	private final CatalogueVersionBumper catalogueVersionBumper;
	private final AdminAuditWriter auditWriter;
	private final MongoTemplate mongo;
	private final AdminScopeAuthorizer adminScope;

	// ---------------------------------------------------------------- list

	public PageResponse<PublisherView> list(String q, RecordStatus status, PageQuery pageQuery) {
		adminScope.requireSuperAdmin();

		List<Criteria> parts = new ArrayList<>();
		if (status != null) {
			parts.add(Criteria.where("status").is(status));
		}
		if (q != null && !q.isBlank()) {
			// Case-insensitive prefix match on name, same pattern as
			// InstitutionSearchRepository.
			String escaped = escapeRegex(q.trim());
			parts.add(Criteria.where("name").regex("(^|\\s)" + escaped, "i"));
		}

		Query query = parts.isEmpty()
				? new Query()
				: new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
		query.with(Sort.by(Sort.Direction.ASC, "name"));

		long total = mongo.count(Query.of(query).limit(0).skip(0), Publisher.class);
		query.skip((long) pageQuery.page() * pageQuery.size()).limit(pageQuery.size());
		List<Publisher> items = mongo.find(query, Publisher.class);

		List<PublisherView> views = items.stream().map(this::toView).toList();
		return new PageResponse<>(views, pageQuery.page(), pageQuery.size(), total);
	}

	// ---------------------------------------------------------------- create

	public PublisherView create(PublisherWrite write) {
		// findByCode normalises to upper-case inside the entity; match that here.
		String normalised = write.code().toUpperCase();
		publisherRepository.findByCode(normalised).ifPresent(existing -> {
			throw new ApiException(ErrorCode.CODE_TAKEN, "Publisher code '" + write.code() + "' is already taken");
		});

		Publisher publisher = new Publisher();
		publisher.setCode(write.code()); // entity normalises to upper-case
		publisher.setName(write.name());
		publisher.setDescription(write.description());
		publisher.setLogoUrl(write.logoUrl());
		publisher.setStatus(RecordStatus.ACTIVE);
		publisher.setCreatedAt(Instant.now());
		publisher.setUpdatedAt(Instant.now());

		publisher = publisherRepository.save(publisher);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.CREATE, "PUBLISHER", publisher.getId(), null,
				afterMap(publisher));

		return toView(publisher);
	}

	// ---------------------------------------------------------------- get

	public PublisherView get(String publisherId) {
		requireAccess(publisherId);
		return publisherRepository.findById(publisherId).map(this::toView)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));
	}

	// ---------------------------------------------------------------- update

	public PublisherView update(String publisherId, PublisherWrite write) {
		requireAccess(publisherId);
		Publisher publisher = publisherRepository.findById(publisherId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));

		Map<String, Object> before = afterMap(publisher);

		publisher.setCode(write.code());
		publisher.setName(write.name());
		publisher.setDescription(write.description());
		publisher.setLogoUrl(write.logoUrl());
		publisher.setUpdatedAt(Instant.now());

		publisher = publisherRepository.save(publisher);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.UPDATE, "PUBLISHER", publisher.getId(), before,
				afterMap(publisher));

		return toView(publisher);
	}

	// ---------------------------------------------------------------- status

	public PublisherView changeStatus(String publisherId, StatusChange change) {
		requireAccess(publisherId);
		Publisher publisher = publisherRepository.findById(publisherId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));

		RecordStatus oldStatus = publisher.getStatus();
		RecordStatus newStatus = change.status();

		Map<String, Object> before = Map.of("status", String.valueOf(oldStatus));

		publisher.setStatus(newStatus);
		publisher.setUpdatedAt(Instant.now());
		publisher = publisherRepository.save(publisher);

		Map<String, Object> meta = change.reason() != null && !change.reason().isBlank()
				? Map.of("reason", change.reason())
				: null;

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.STATUS, "PUBLISHER", publisher.getId(), before,
				Map.of("status", String.valueOf(newStatus)), meta);

		// Suspending or reactivating affects what feeds serve — bump catalogue version.
		// RETIRED is terminal and affects no feed.
		if (newStatus == RecordStatus.ACTIVE || newStatus == RecordStatus.SUSPENDED) {
			catalogueVersionBumper.bump(CatalogueVersionBumper.Scope.PUBLISHER, publisherId);
		}

		return toView(publisher);
	}

	private void requireAccess(String publisherId) {
		if (!adminScope.canAccessPublisher(publisherId)) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to access this publisher");
		}
	}

	// ---------------------------------------------------------------- mapping

	private PublisherView toView(Publisher p) {
		long itemCount = catalogueItemRepository.countByPublisherId(p.getId());
		long collectionCount = bookCollectionRepository.countByPublisherId(p.getId());
		return new PublisherView(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getLogoUrl(), p.getStatus(),
				itemCount, collectionCount, p.getCreatedAt());
	}

	private static Map<String, Object> afterMap(Publisher p) {
		// Only the mutable fields that an operator changes; id and createdAt are never
		// edited.
		return Map.of("code", String.valueOf(p.getCode()), "name", String.valueOf(p.getName()), "status",
				String.valueOf(p.getStatus()));
	}

	// ---------------------------------------------------------------- helpers

	private static final String REGEX_META = "\\^$.|?*+()[]{}";

	private static String escapeRegex(String term) {
		StringBuilder out = new StringBuilder(term.length() + 8);
		for (char ch : term.toCharArray()) {
			if (REGEX_META.indexOf(ch) >= 0) {
				out.append('\\');
			}
			out.append(ch);
		}
		return out.toString();
	}
}
