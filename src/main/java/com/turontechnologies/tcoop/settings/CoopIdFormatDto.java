package com.turontechnologies.tcoop.settings;

/** Settings -> Payment Settings -> Fees & Charges' "Co-op ID Format" section — how
 * {@code GET /cooperatives/next-id} builds the next auto-generated co-op id. {@code type} is
 * NUMERIC (0-9), ALPHA (A-Z), or ALPHANUMERIC (0-9 then A-Z). */
public record CoopIdFormatDto(String prefix, int padding, String type) {

  public static CoopIdFormatDto from(PlatformSettings settings) {
    return new CoopIdFormatDto(settings.getCoopIdPrefix(), settings.getCoopIdPadding(), settings.getCoopIdType());
  }
}
