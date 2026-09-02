package com.tf.reader.auth.repository;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.TnfUser;

/**
 * Maps an authenticated identity, in the context of one institution, onto a TnF user - the real
 * thing {@code MockUserRepository} stood in for. Backed by the {@code readerUsers} collection, so
 * provisioning a reader is inserting a document, not shipping a code change.
 *
 * <p><b>The key is the pair, not the email.</b> One identity may be a member of several
 * institutions and is a different user in each - different userId, different entitled
 * collections. That is what lets a single IdP serve every institution: the assertion says who
 * someone is, and the institution chosen at the start of the transaction says which membership
 * of theirs we are acting on.
 */
@Component
public class ReaderUserDirectory {

	private final ReaderUserRepository readerUsers;

	public ReaderUserDirectory(ReaderUserRepository readerUsers) {
		this.readerUsers = readerUsers;
	}

	/**
	 * @return the user this identity is, at this institution, or empty if they hold no
	 *         membership there - authenticated is not the same as provisioned
	 */
	public Optional<TnfUser> find(String email, String institutionId) {
		if (email == null || institutionId == null) {
			return Optional.empty();
		}
		// Locale.ROOT, not the JVM default: in a Turkish or Azeri locale "I".toLowerCase() is the
		// dotless "ı", so an address containing a capital I would fail to match a stored key and a
		// provisioned user would be refused as unprovisioned on some machines and not others. An
		// email address is protocol data and is folded the same way everywhere.
		String folded = email.trim().toLowerCase(Locale.ROOT);
		return readerUsers.findByEmailAndInstitutionId(folded, institutionId).map(ReaderUser::toTnfUser);
	}
}
