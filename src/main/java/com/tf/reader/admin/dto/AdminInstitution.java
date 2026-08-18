package com.tf.reader.admin.dto;

import com.tf.reader.catalogue.dto.BrandingView;
import com.tf.reader.catalogue.dto.SignInView;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.common.model.RecordStatus;

import java.util.List;

/**
 * The full admin view of an institution: everything a member of the public sees, plus its type,
 * status, catalogue version and a short summary. Email domains are always empty for now — nothing
 * stores them yet, though a request may still include them.
 */
public record AdminInstitution(
        String id,
        String code,
        String name,
        String country,
        String city,
        BrandingView branding,
        SignInView signIn,
        String catalogueUrl,
        InstitutionType type,
        List<String> emailDomains,
        RecordStatus status,
        long catalogueVersion,
        InstitutionSummary summary) {}
