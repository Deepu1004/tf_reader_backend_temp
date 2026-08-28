package com.tf.reader.ingest.service;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;

/**
 * Whether an asset is locked (whole-file AES-256-GCM under a BEK). Settled policy, implemented
 * once here so {@link IngestService} (the size cap) and {@link AssetLocker} (the encryption
 * branch) never drift apart: open access is never encrypted, regardless of format; SUBSCRIPTION
 * and ELITE are, including audio. Whole-file encryption still can't be seeked into without a full
 * decrypt first - accepted deliberately for locked audio rather than left unencrypted, so the
 * device pays for that on playback, not the server on this check.
 */
final class TierRules {

	private TierRules() {
	}

	static boolean requiresLocking(AccessTier tier, ContentType type) {
		return tier == AccessTier.SUBSCRIPTION || tier == AccessTier.ELITE;
	}

}
