package com.tf.reader.catalogue.opds.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tf.reader.catalogue.opds.dto.OpdsPublicationDocument;
import com.tf.reader.catalogue.opds.service.OpdsPublicFeedService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

// Plain unit test calling the controller directly, same pattern as OpdsCatalogueControllerTest -
// dispatch is this class's own logic, feed/document building is OpdsPublicFeedService's, already
// covered by OpdsPublicFeedServiceIT and the schema validation IT.
class OpdsPublicCatalogueControllerTest {

	private final OpdsPublicFeedService publicFeedService = mock(OpdsPublicFeedService.class);
	private final OpdsPublicCatalogueController controller = new OpdsPublicCatalogueController(publicFeedService);

	@Test
	void publicationReturnsTheDocumentWithAPublicCacheHeader() {
		OpdsPublicationDocument document =
				new OpdsPublicationDocument("https://readium.org/webpub-manifest/context.jsonld", null, null, null);
		when(publicFeedService.publicationDocument("item_42")).thenReturn(document);

		ResponseEntity<OpdsPublicationDocument> response = controller.publication("item_42");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(document);
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("max-age=300, public");
	}

	@Test
	void publicationPropagatesNotFoundFromTheService() {
		when(publicFeedService.publicationDocument("item_missing"))
				.thenThrow(new ApiException(ErrorCode.NOT_FOUND, "No such item"));

		assertThatThrownBy(() -> controller.publication("item_missing"))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
