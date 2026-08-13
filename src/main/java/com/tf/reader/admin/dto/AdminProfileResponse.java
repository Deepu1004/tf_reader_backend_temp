package com.tf.reader.admin.dto;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The authenticated admin's own profile.
 *
 * <p>An explicit projection rather than the entity, so {@code passwordHash} cannot reach a response
 * or the generated schema even if the entity later gains fields.
 */
@Schema(name = "AdminProfileResponse", description = "Profile of the currently authenticated admin.")
public record AdminProfileResponse(

		String id,
		String email,
		String name,
		AdminRole role,

		@Schema(description = "Publisher this admin is scoped to. Null unless the admin is publisher-scoped.")
		String publisherId,

		@Schema(description = "Institution this admin is scoped to. Null unless the admin is institution-scoped.")
		String institutionId,

		AdminStatus status) {

	public static AdminProfileResponse from(AdminUser adminUser) {
		return new AdminProfileResponse(adminUser.getId(), adminUser.getEmail(), adminUser.getName(),
				adminUser.getRole(), adminUser.getPublisherId(), adminUser.getInstitutionId(),
				adminUser.getStatus());
	}

}
