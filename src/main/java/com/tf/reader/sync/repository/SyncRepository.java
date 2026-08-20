package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.BaseDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Query methods shared by every synchronised collection.
 * "IsDeletedFalse" variants hide tombstoned records.
 */
@NoRepositoryBean
public interface SyncRepository<T extends BaseDocument> extends MongoRepository<T, String> {

    List<T> findByIsDeletedFalse();

    List<T> findByUserId(String userId);

    List<T> findByUserIdAndIsDeletedFalse(String userId);
}
