package com.tf.reader.hold.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tf.reader.hold.api.HoldSnapshot;
import com.tf.reader.hold.api.HoldSnapshotQuery;

/** Stub — reports no holds for any user. */
@Service
class HoldSnapshotQueryImpl implements HoldSnapshotQuery {

	@Override
	public List<HoldSnapshot> holdsFor(String userId) {
		return List.of();
	}
}
