package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.Licence;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code licId} is unique, so it yields at most one licence. A book can carry
 * several licences over time (renewals), hence the list.
 */
@Repository
public interface LicenceRepository extends MongoRepository<Licence, String> {

    Optional<Licence> findFirstByLicId(String licId);

    List<Licence> findByBookId(String bookId);
}
