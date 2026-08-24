package com.tf.reader.catalogue.opds.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.opds.dto.OpdsFeedMetadata;
import com.tf.reader.catalogue.opds.dto.OpdsNavigationFeed;
import com.tf.reader.catalogue.opds.service.OpdsFeedService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

// Plain unit test calling the controller directly, same pattern as HoldControllerTest - the
// mismatch check and the ETag short-circuit are this class's own logic, not the security
// chain's, so there is nothing here that needs a real HTTP round trip.
class OpdsCatalogueControllerTest {

	private final OpdsFeedService feedService = mock(OpdsFeedService.class);
	private final OpdsCatalogueController controller = new OpdsCatalogueController(feedService);

	private final CurrentUser member = new CurrentUser("user_1", UserType.INSTITUTION, "inst_1", List.of(), List.of());

	private Institution institution(long catalogueVersion) {
		Institution institution = new Institution();
		institution.setId("inst_1");
		institution.setCatalogueVersion(catalogueVersion);
		return institution;
	}

	@Test
	void rootFeedRejectsATokenForADifferentInstitution() {
		CurrentUser otherInstitution = new CurrentUser("user_2", UserType.INSTITUTION, "inst_2", List.of(), List.of());

		assertThatThrownBy(() -> controller.rootFeed("inst_1", otherInstitution, null))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode())
						.isEqualTo(ErrorCode.FORBIDDEN_INSTITUTION_MISMATCH));
	}

	@Test
	void rootFeedReturnsTheFeedWithAnEtagWhenNothingMatches() {
		Institution institution = institution(3);
		when(feedService.loadInstitution("inst_1")).thenReturn(institution);
		OpdsNavigationFeed feed = new OpdsNavigationFeed(new OpdsFeedMetadata("Imperial", null, null, null, null),
				List.of(), List.of(), null);
		when(feedService.rootFeed(eq(institution), any())).thenReturn(feed);

		ResponseEntity<OpdsNavigationFeed> response = controller.rootFeed("inst_1", member, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(feed);
		assertThat(response.getHeaders().getETag()).isEqualTo("W/\"inst_1-3\"");
	}

	@Test
	void rootFeedShortCircuitsToNotModifiedWhenTheEtagMatches() {
		when(feedService.loadInstitution("inst_1")).thenReturn(institution(3));

		ResponseEntity<OpdsNavigationFeed> response = controller.rootFeed("inst_1", member, "W/\"inst_1-3\"");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
		assertThat(response.getBody()).isNull();
	}

	@Test
	void groupFeedRejectsATokenForADifferentInstitution() {
		CurrentUser otherInstitution = new CurrentUser("user_2", UserType.INSTITUTION, "inst_2", List.of(), List.of());

		assertThatThrownBy(() -> controller.groupFeed("inst_1", "all", otherInstitution, null, null, null, null, null))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode())
						.isEqualTo(ErrorCode.FORBIDDEN_INSTITUTION_MISMATCH));
	}
}
