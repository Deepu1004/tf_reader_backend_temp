package com.tf.reader.admin.dto;

/** Quick counts for one institution: how many active entitlements it has, how many books it can
 * reach, and the address of its catalogue feed. */
public record InstitutionSummary(long entitlementCount, long accessibleItemCount, String feedUrl) {}
