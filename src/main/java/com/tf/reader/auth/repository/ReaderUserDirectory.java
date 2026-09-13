package com.tf.reader.auth.repository;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.tf.reader.auth.entity.ReaderUser;
import com.tf.reader.auth.model.Role;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;

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

	private static final SecureRandom RANDOM = new SecureRandom();

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

	/**
	 * The individual counterpart of {@link #find(String, String)}: no institution to key by, and
	 * unlike that lookup, authenticated here <b>is</b> provisioned. A B2C email-and-password
	 * sign-up is a new person by definition the first time it succeeds, so there is no roster to
	 * pre-provision against the way an institution's is.
	 *
	 * @return the existing individual reader for this email, or a newly provisioned one
	 */
	public TnfUser findOrProvisionIndividual(String email) {
		String folded = email.trim().toLowerCase(Locale.ROOT);
		return readerUsers.findByEmailAndInstitutionIdIsNull(folded)
				.orElseGet(() -> provisionIndividual(folded))
				.toTnfUser();
	}

	private ReaderUser provisionIndividual(String foldedEmail) {
		try {
			return readerUsers.save(ReaderUser.builder()
					.id(newIndividualUserId())
					.email(foldedEmail)
					.type(UserType.INDIVIDUAL)
					.institutionId(null)
					.roles(List.of(Role.SUBSCRIBER.name()))
					.collections(List.of())
					.build());
		}
		catch (DuplicateKeyException concurrentSignUp) {
			// Two sign-ins for the same brand-new email at once: the loser here is not a failure -
			// the email is provisioned either way, just not by this call.
			return readerUsers.findByEmailAndInstitutionIdIsNull(foldedEmail)
					.orElseThrow(() -> concurrentSignUp);
		}
	}

	private static String newIndividualUserId() {
		byte[] bytes = new byte[8];
		RANDOM.nextBytes(bytes);
		return "usr_" + HexFormat.of().formatHex(bytes);
	}
}
