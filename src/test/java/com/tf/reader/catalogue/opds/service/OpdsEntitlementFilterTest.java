package com.tf.reader.catalogue.opds.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.repository.PublisherRepository;

/** One entitlement lookup per feed request, not one per item - proved by call counts, not timing. */
class OpdsEntitlementFilterTest {

    private static final SubjectRef SUBJECT = new SubjectRef("u_88", "inst_7f3");

    private final EntitlementQuery entitlementQuery = mock(EntitlementQuery.class);
    private final OpdsPublicationMapper publicationMapper = mock(OpdsPublicationMapper.class);
    private final PublisherRepository publisherRepository = mock(PublisherRepository.class);

    private final OpdsEntitlementFilter filter = new OpdsEntitlementFilter(entitlementQuery, publicationMapper,
            publisherRepository);

    private static CatalogueItem item(String id) {
        CatalogueItem item = new CatalogueItem();
        item.setId(id);
        item.setPublisherId("pub_1");
        return item;
    }

    private static EntitlementDecision entitled() {
        return new EntitlementDecision(true, AccessLevel.ENTITLED_UNLIMITED, "ent_1", null, 14, null, null);
    }

    private static EntitlementDecision denied() {
        return new EntitlementDecision(false, null, null, null, 0, null, DenyReason.NO_ENTITLEMENT);
    }

    @Test
    void mapEntitledChecksEntitlementOnceForTheWholeList() {
        CatalogueItem allowed = item("item_a");
        CatalogueItem blocked = item("item_b");
        when(publisherRepository.findAllById(any())).thenReturn(List.of());
        when(entitlementQuery.checkAll(SUBJECT, List.of("item_a", "item_b")))
                .thenReturn(Map.of("item_a", entitled(), "item_b", denied()));
        when(publicationMapper.toPublication(eq(allowed), any(), any(), any()))
                .thenReturn(new OpdsPublication(null, List.of(), List.of()));

        List<OpdsPublication> result = filter.mapEntitled(List.of(allowed, blocked), "inst_7f3", SUBJECT);

        assertThat(result).hasSize(1);
        verify(entitlementQuery, times(1)).checkAll(any(), any());
        verify(entitlementQuery, times(0)).check(any(), any());
    }

    @Test
    void countEntitledChecksEntitlementOnceForTheWholeList() {
        CatalogueItem allowed = item("item_a");
        CatalogueItem blocked = item("item_b");
        when(entitlementQuery.checkAll(SUBJECT, List.of("item_a", "item_b")))
                .thenReturn(Map.of("item_a", entitled(), "item_b", denied()));

        int count = filter.countEntitled(List.of(allowed, blocked), SUBJECT);

        assertThat(count).isEqualTo(1);
        verify(entitlementQuery, times(1)).checkAll(any(), any());
        verify(entitlementQuery, times(0)).check(any(), any());
    }

}
