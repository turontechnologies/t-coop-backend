package com.turontechnologies.tcoop.cooperative;

/** GET /api/v1/cooperatives/{id}/branding — just enough for a member (any role, not only staff)
 * to see which co-operative they belong to, and in what currency, on their own dashboard. The
 * full CooperativeSummaryDto carries bank details/subscription info a plain member has no
 * business seeing. */
public record CooperativeBrandingDto(
    String id,
    String name,
    String logoUrl,
    String currency,
    /** What this co-op calls the alternative ID, e.g. "Staff No." — shown wherever it appears. */
    String alternativeIdLabel) {

  public static CooperativeBrandingDto from(Cooperative coop) {
    return new CooperativeBrandingDto(
        coop.getId(), coop.getName(), coop.getLogoUrl(), coop.getCurrency(), coop.getAlternativeIdLabel());
  }
}
