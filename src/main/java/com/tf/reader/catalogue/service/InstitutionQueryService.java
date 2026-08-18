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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Everything the two public institution endpoints decide. The controller does HTTP and nothing else,
 * so a second endpoint added later cannot skip these rules.
 *
 * <p>Entity to DTO mapping is by hand: no copier, no mapper library. A field Person B adds to
 * {@code Institution} next month reaches the wire only if somebody types it here. That is the leak
 * guard, and it is a design choice rather than a test.
 *
 * <p>Person B's entity is a Lombok class, so the accessors are {@code getX()} and {@code SignIn} is
 * nested inside {@code Institution}.
 */
@Service
public class InstitutionQueryService {

    private final InstitutionSearchRepository institutions;
    private final String catalogueBaseUrl;

    /** The only sign-in method the prototype has. The contract says it is not configurable. */
    private static final String SIGN_IN_METHOD = "SAML";

    public InstitutionQueryService(
            InstitutionSearchRepository institutions,
            @Value("${tnf.catalogue.base-url:http://localhost:8080}") String catalogueBaseUrl) {
        this.institutions = institutions;
        this.catalogueBaseUrl = stripTrailingSlash(catalogueBaseUrl);
    }

    /**
     * The find-your-institution list. ACTIVE only, name ascending, paged.
     *
     * <p>No matches is a 200 with an empty array and a correct total, including a page past the end.
     * This is plain JSON, not an OPDS feed, so the no-empty-arrays rule does not apply.
     */
    public PageResponse<InstitutionListItem> list(ListRequest request) {
        InstitutionSearchRepository.Results results =
                institutions.search(request.q(), request.country(), request.page(), request.size());

        List<InstitutionListItem> items = results.items().stream().map(this::toListItem).toList();
        return new PageResponse<>(items, request.page(), request.size(), results.total());
    }

    /**
     * One institution.
     *
     * <p>Unknown and inactive both give 404 with the same body. Not 403: that would confirm the id
     * exists and let a stranger map our customer list by walking ids.
     */
    public InstitutionDetail detail(String institutionId) {
        return institutions
                .findActiveById(institutionId)
                .map(this::toDetail)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such institution"));
    }

    // ---------------------------------------------------------------------------------- mapping

    private InstitutionListItem toListItem(Institution i) {
        // Not mapped, on purpose: type (internal), status (only ACTIVE gets here), catalogueVersion
        // (a cache key), createdAt and updatedAt (operational detail nobody asked for).
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
                catalogueUrlFor(i.getId()));
    }

    private BrandingView toBranding(Institution i) {
        // A public endpoint must not 500 because one record was saved without branding.
        return i.getBranding() == null
                ? null
                : new BrandingView(i.getBranding().getLogoUrl(), i.getBranding().getPrimaryColor());
    }

    private SignInView toSignIn(Institution i) {
        // idpHint comes from the record; method is the constant above.
        String idpHint = i.getSignIn() == null ? null : i.getSignIn().getIdpHint();
        return new SignInView(SIGN_IN_METHOD, idpHint);
    }

    private String catalogueUrlFor(String institutionId) {
        return catalogueBaseUrl + "/opds/v1/institutions/" + institutionId + "/catalogue";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // --------------------------------------------------------------------- request normalisation

    /**
     * The validated form of the four query parameters. {@link #of} is the only way to build one, so
     * nothing downstream ever sees a raw string from a URL.
     */
    public record ListRequest(String q, String country, int page, int size) {

        public static final int DEFAULT_SIZE = 20;
        public static final int MIN_SIZE = 1;
        public static final int MAX_SIZE = 100;

        /**
         * Trims, defaults and validates.
         *
         * <p>Both out-of-range parameters are rejected rather than corrected. An earlier draft clamped
         * {@code size} into range; the published contract documents a 400 for it, and silently serving
         * 100 rows to a client that asked for 5000 is a paging bug the client cannot see.
         */
        public static ListRequest of(String q, String country, Integer page, Integer size) {
            String normalisedQ = blankToNull(q);
            String normalisedCountry = blankToNull(country);

            int resolvedPage = page == null ? 0 : page;
            if (resolvedPage < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be zero or greater");
            }

            int resolvedSize = size == null ? DEFAULT_SIZE : size;
            if (resolvedSize < MIN_SIZE || resolvedSize > MAX_SIZE) {
                // Message copied verbatim from the contract's 400 example.
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