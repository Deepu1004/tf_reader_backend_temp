package com.tf.reader.catalogue.service;

import com.tf.reader.catalogue.dto.BrandingView;
import com.tf.reader.catalogue.dto.InstitutionDetail;
import com.tf.reader.catalogue.dto.InstitutionListItem;
import com.tf.reader.catalogue.dto.SignInView;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.repository.InstitutionSearchRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.page.PageResponse;

import org.springframework.stereotype.Service;

import java.util.List;

/** Handles the two public institution endpoints: the searchable list and one institution's detail. */
@Service
public class InstitutionQueryService {

    private final InstitutionSearchRepository institutions;
    private final CatalogueUrlBuilder catalogueUrlBuilder;

    private static final String SIGN_IN_METHOD = "SAML";

    public InstitutionQueryService(
            InstitutionSearchRepository institutions, CatalogueUrlBuilder catalogueUrlBuilder) {
        this.institutions = institutions;
        this.catalogueUrlBuilder = catalogueUrlBuilder;
    }

    /** Only active institutions appear here, sorted by name, with a page that can be empty. */
    public PageResponse<InstitutionListItem> list(ListRequest request) {
        InstitutionSearchRepository.Results results =
                institutions.search(request.q(), request.country(), request.page(), request.size());

        List<InstitutionListItem> items = results.items().stream().map(this::toListItem).toList();
        return new PageResponse<>(items, request.page(), request.size(), results.total());
    }

    /** Looks up one active institution by id, or reports it as not found. */
    public InstitutionDetail detail(String institutionId) {
        return institutions
                .findActiveById(institutionId)
                .map(this::toDetail)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such institution"));
    }

    // ---------------------------------------------------------------------------------- mapping

    private InstitutionListItem toListItem(Institution i) {
        return new InstitutionListItem(
                i.getId(), i.getCode(), i.getName(), i.getCountry(), i.getCity(), toBranding(i));
    }

    private InstitutionDetail toDetail(Institution i) {
        return new InstitutionDetail(
                i.getId(),
                i.getCode(),
                i.getName(),
                i.getCountry(),
                i.getCity(),
                toBranding(i),
                toSignIn(i),
                catalogueUrlBuilder.catalogueUrlFor(i.getId()));
    }

    private BrandingView toBranding(Institution i) {
        return i.getBranding() == null
                ? null
                : new BrandingView(i.getBranding().getLogoUrl(), i.getBranding().getPrimaryColor());
    }

    private SignInView toSignIn(Institution i) {
        String idpHint = i.getSignIn() == null ? null : i.getSignIn().getIdpHint();
        return new SignInView(SIGN_IN_METHOD, idpHint);
    }

    // --------------------------------------------------------------------- request normalisation

    /** A checked, defaulted version of the four list parameters. */
    public record ListRequest(String q, String country, int page, int size) {

        public static final int DEFAULT_SIZE = 20;
        public static final int MIN_SIZE = 1;
        public static final int MAX_SIZE = 100;

        public static ListRequest of(String q, String country, Integer page, Integer size) {
            String normalisedQ = blankToNull(q);
            String normalisedCountry = blankToNull(country);

            int resolvedPage = page == null ? 0 : page;
            if (resolvedPage < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be zero or greater");
            }

            int resolvedSize = size == null ? DEFAULT_SIZE : size;
            if (resolvedSize < MIN_SIZE || resolvedSize > MAX_SIZE) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "size must be between " + MIN_SIZE + " and " + MAX_SIZE);
            }

            return new ListRequest(normalisedQ, normalisedCountry, resolvedPage, resolvedSize);
        }

        private static String blankToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
