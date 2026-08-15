package com.tf.reader.catalogue.controller;

import com.tf.reader.catalogue.dto.InstitutionDetail;
import com.tf.reader.catalogue.dto.InstitutionListItem;
import com.tf.reader.catalogue.service.InstitutionQueryService;
import com.tf.reader.catalogue.service.InstitutionQueryService.ListRequest;
import com.tf.reader.common.page.PageResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two public institution endpoints, and the only two in the system that take no token.
 *
 * <pre>
 *   GET /api/v1/institutions?q=&amp;country=&amp;page=&amp;size=
 *   GET /api/v1/institutions/{institutionId}
 * </pre>
 *
 * <p>They are open because a user has to find their institution before they can sign in to it.
 * Requiring a token here would be a deadlock. Tokens are ignored, not rejected: someone whose session
 * expired still needs to reach the picker.
 *
 * <p><b>The bean name is explicit for a reason.</b> {@code repo-structure.md} also has an
 * {@code InstitutionController} in {@code admin/controller}. Spring derives bean names from the simple
 * class name, so two of them means {@code ConflictingBeanDefinitionException} at startup, for
 * everybody, the day the second one merges. Naming ours fixes it without asking anyone to rename.
 *
 * <p>HTTP only. Every rule about what a stranger may see is in {@link InstitutionQueryService}.
 */
@RestController("publicInstitutionController")
@RequestMapping("/api/v1/institutions")
public class InstitutionController {

    private final InstitutionQueryService institutions;

    public InstitutionController(InstitutionQueryService institutions) {
        this.institutions = institutions;
    }

    /**
     * The find-your-institution list. No token.
     *
     * <p>{@code page} and {@code size} are boxed so "absent" and "zero" stay distinguishable:
     * {@code size=0} is a client asking for nothing and gets a 400, while an absent size gets 20.
     *
     * <p>{@code page=abc} never reaches this method. Spring fails to bind it and Person B's
     * {@code GlobalExceptionHandler} turns the type mismatch into a 400 in the shared envelope.
     */
    @GetMapping
    public PageResponse<InstitutionListItem> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return institutions.list(ListRequest.of(q, country, page, size));
    }

    /** One institution by id. No token. Unknown, inactive and malformed all give the same 404. */
    @GetMapping("/{institutionId}")
    public InstitutionDetail detail(@PathVariable String institutionId) {
        return institutions.detail(institutionId);
    }
}