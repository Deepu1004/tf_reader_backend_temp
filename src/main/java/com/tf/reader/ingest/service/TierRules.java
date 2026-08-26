package com.tf.reader.ingest.service;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;

/**
 * Whether an asset is locked (whole-file AES-256-GCM under a BEK). Settled policy, implemented
 * once here so {@link IngestService} (the size cap) and {@link AssetLocker} (the encryption
 * branch) never drift apart: audio is never encrypted in any tier; open access is never
 * encrypted; SUBSCRIPTION and ELITE are.
 */
final class TierRules {

	private TierRules() {
	}

	static boolean requiresLocking(AccessTier tier, ContentType type) {
		if (type == ContentType.AUDIO) {
			return false;
		}
		return tier == AccessTier.SUBSCRIPTION || tier == AccessTier.ELITE;
	}

}
