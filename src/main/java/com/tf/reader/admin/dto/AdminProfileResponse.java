package com.tf.reader.admin.dto;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An admin as the API exposes them: the contract's {@code AdminUser} shape. An explicit projection
 * rather than the entity, so {@code passwordHash} cannot reach a response even if the entity gains
 * fields later.
 */
@Schema(name = "AdminUser", description = "An admin user.")
public record AdminProfileResponse(

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,

		@Schema(format = "email", requiredMode = Schema.RequiredMode.REQUIRED) String email,

		String name,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminRole role,

		@Schema(description = "Publisher this admin is scoped to. Null unless the admin is publisher-scoped.")
		String scopePublisherId,

		@Schema(description = "Institution this admin is scoped to. Null unless the admin is institution-scoped.")
		String scopeInstitutionId,

		AdminStatus status) {

	public static AdminProfileResponse from(AdminUser adminUser) {
		return new AdminProfileResponse(adminUser.getId(), adminUser.getEmail(), adminUser.getName(),
				adminUser.getRole(), adminUser.getPublisherId(), adminUser.getInstitutionId(),
				adminUser.getStatus());
	}

}
