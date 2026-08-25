package com.tf.reader.catalogue.opds.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.opds.service.OpdsPublicFeedService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.page.PageQuery;

class OpdsPublicCatalogueControllerSearchValidationTest {

    private final OpdsPublicCatalogueController controller =
            new OpdsPublicCatalogueController(mock(OpdsPublicFeedService.class));

    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> controller.search("   ", new PageQuery(0, 20), null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsAControlCharacterLikeANullByte() {
        String queryWithNullByte = "foo" + (char) 0 + "bar";
        assertThatThrownBy(() -> controller.search(queryWithNullByte, new PageQuery(0, 20), null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void acceptsAnOrdinaryQuery() {
        assertThat(controller.search("robots", new PageQuery(0, 20), null, null).getStatusCode().value())
                .isEqualTo(200);
    }
}
