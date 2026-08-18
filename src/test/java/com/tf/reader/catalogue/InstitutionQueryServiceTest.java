package com.tf.reader.catalogue;

import com.tf.reader.catalogue.dto.InstitutionDetail;
import com.tf.reader.catalogue.dto.InstitutionListItem;
import com.tf.reader.catalogue.entity.Branding;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.catalogue.repository.InstitutionSearchRepository;
import com.tf.reader.catalogue.service.InstitutionQueryService;
import com.tf.reader.catalogue.service.InstitutionQueryService.ListRequest;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules that decide what a stranger may see, tested without a servlet or a database.
 *
 * <p>The entity fixtures below are built against Person B's real classes as delivered in
 * ACADEMIC.docx: Lombok {@code @AllArgsConstructor} in handbook section 06 field order, with
 * {@code SignIn} nested inside {@code Institution} and {@code Branding} top-level.
 */
class InstitutionQueryServiceTest {

    private static final Instant T = Instant.parse("2026-08-10T09:00:00Z");

    private InstitutionSearchRepository repository;
    private InstitutionQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(InstitutionSearchRepository.class);
        service = new InstitutionQueryService(repository, "http://localhost:8080");
    }

    private static Institution imperial() {
        return new Institution(
                "inst_7f3",
                "imperial",
                "Imperial College London",
                InstitutionType.ACADEMIC,
                "UK",
                "London",
                new Branding("https://cdn.tf.example/logos/imperial.png", "#003E74"),
                new Institution.SignIn("SAML", "imperial-saml-mock"),
                RecordStatus.ACTIVE,
                14L,
                T,
                T);
    }

    // ------------------------------------------------------------------------------------ list

    @Test
    @DisplayName("a list item carries exactly the six frozen fields, mapped correctly")
    void listItemIsTheFrozenShape() {
        when(repository.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(new InstitutionSearchRepository.Results(List.of(imperial()), 1));

        PageResponse<InstitutionListItem> page = service.list(ListRequest.of(null, null, null, null));

        assertThat(page.items()).hasSize(1);
        InstitutionListItem item = page.items().get(0);
        assertThat(item.id()).isEqualTo("inst_7f3");
        assertThat(item.code()).isEqualTo("imperial");
        assertThat(item.name()).isEqualTo("Imperial College London");
        assertThat(item.country()).isEqualTo("UK");
        assertThat(item.city()).isEqualTo("London");
        assertThat(item.branding().logoUrl()).isEqualTo("https://cdn.tf.example/logos/imperial.png");
        assertThat(item.branding().primaryColor()).isEqualTo("#003E74");
    }

    @Test
    @DisplayName("the list item record has six components and no seventh can be added by accident")
    void listItemHasNoExtraComponents() {
        // Reflection over the record rather than over a serialised body, so this fails at the moment
        // somebody adds a field to the DTO, not later when a leak test happens to run.
        assertThat(InstitutionListItem.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("id", "code", "name", "country", "city", "branding");

        assertThat(InstitutionDetail.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly(
                        "id", "code", "name", "country", "city", "branding", "signIn", "catalogueUrl");
    }

    @Test
    @DisplayName("nothing internal survives the mapping")
    void internalFieldsAreNotMapped() {
        // type, status and catalogueVersion all exist on the entity and none of them has anywhere to
        // go on the DTO. Asserted as an absence of record components, because the mapping is explicit
        // and by hand: a field reaches the wire only if somebody typed it.
        List<String> listItemFields =
                List.of(InstitutionListItem.class.getRecordComponents()).stream()
                        .map(RecordComponent::getName)
                        .toList();
        assertThat(listItemFields)
                .doesNotContain("type", "status", "catalogueVersion", "createdAt", "updatedAt");
    }

    @Test
    @DisplayName("the page wrapper echoes the request, and total is the full match count")
    void pagingIsEchoedAndTotalIsTheWholeMatch() {
        when(repository.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(new InstitutionSearchRepository.Results(List.of(imperial()), 57));

        PageResponse<InstitutionListItem> page = service.list(ListRequest.of(null, null, 2, 10));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).as("total counts every match, not the page").isEqualTo(57);
    }

    @Test
    @DisplayName("normalised parameters, not raw ones, reach the query layer")
    void normalisedParametersReachTheRepository() {
        when(repository.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(new InstitutionSearchRepository.Results(List.of(), 0));

        service.list(ListRequest.of("  Imperial ", "  uk  ", null, 50));

        ArgumentCaptor<String> q = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> country = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(repository).search(q.capture(), country.capture(), page.capture(), size.capture());

        assertThat(q.getValue()).isEqualTo("Imperial");
        assertThat(country.getValue()).as("trimmed, not upper-cased").isEqualTo("uk");
        assertThat(page.getValue()).isZero();
        assertThat(size.getValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("no matches is an empty list, not an error")
    void emptyResultIsNotAnError() {
        when(repository.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(new InstitutionSearchRepository.Results(List.of(), 0));

        PageResponse<InstitutionListItem> page = service.list(ListRequest.of("zzz", null, null, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    // ---------------------------------------------------------------------------------- detail

    @Test
    @DisplayName("the detail carries the six plus signIn and a server-built catalogueUrl")
    void detailIsTheFrozenShape() {
        when(repository.findActiveById("inst_7f3")).thenReturn(Optional.of(imperial()));

        InstitutionDetail detail = service.detail("inst_7f3");

        assertThat(detail.signIn().method()).as("always SAML in this prototype").isEqualTo("SAML");
        assertThat(detail.signIn().idpHint())
                .as("read from the record, unlike method")
                .isEqualTo("imperial-saml-mock");
        assertThat(detail.catalogueUrl())
                .isEqualTo("http://localhost:8080/opds/v1/institutions/inst_7f3/catalogue");
    }

    @Test
    @DisplayName("catalogueUrl comes from configuration, so the scheme can change without a client release")
    void catalogueUrlFollowsConfiguration() {
        InstitutionQueryService configured =
                new InstitutionQueryService(repository, "https://api.tf.example/");
        when(repository.findActiveById("inst_7f3")).thenReturn(Optional.of(imperial()));

        assertThat(configured.detail("inst_7f3").catalogueUrl())
                .as("a trailing slash in configuration must not produce a double slash")
                .isEqualTo("https://api.tf.example/opds/v1/institutions/inst_7f3/catalogue");
    }

    @Test
    @DisplayName("unknown and inactive are the same 404, and the message discloses nothing")
    void unknownAndInactiveAreIndistinguishable() {
        // The repository filters on ACTIVE inside the query, so the service cannot tell the two apart
        // either. That is the design: an attacker walking ids learns nothing about our customer list.
        when(repository.findActiveById(any())).thenReturn(Optional.empty());

        for (String id : List.of("inst_leeds", "inst_does_not_exist", "!!not-an-id!!")) {
            assertThatThrownBy(() -> service.detail(id))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("No such institution")
                    .satisfies(
                            e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @Test
    @DisplayName("a record saved without branding returns null, not a 500")
    void missingBrandingDoesNotBreakAPublicEndpoint() {
        Institution incomplete =
                new Institution(
                        "inst_x", "x", "X University", InstitutionType.ACADEMIC, "UK", "York",
                        null, null, RecordStatus.ACTIVE, 1L, T, T);
        when(repository.findActiveById("inst_x")).thenReturn(Optional.of(incomplete));

        InstitutionDetail detail = service.detail("inst_x");

        assertThat(detail.branding()).isNull();
        assertThat(detail.signIn().method()).isEqualTo("SAML");
        assertThat(detail.signIn().idpHint()).isNull();
    }
}