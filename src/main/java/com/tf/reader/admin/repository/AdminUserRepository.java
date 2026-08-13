package com.tf.reader.admin.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.admin.entity.AdminUser;

public interface AdminUserRepository extends MongoRepository<AdminUser, String> {

	Optional<AdminUser> findByEmail(String email);

}
