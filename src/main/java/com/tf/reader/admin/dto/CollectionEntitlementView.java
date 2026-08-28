package com.tf.reader.admin.dto;

/**
 * A collection as the entitlement request screen sees it: identity plus the current
 * institution's own entitlementStatus for the collection as a whole - computed from the
 * institution's COLLECTION/PUBLISHER-scoped entitlements, independent of what any individual
 * item inside the collection shows.
 */
public record CollectionEntitlementView(String id, String publisherId, String code, String name, String description,
		String entitlementStatus) {
}
