package com.turontechnologies.tcoop.settings;

/** Matches the frontend's CollectionAccountSettings shape (src/lib/settings-data.ts). */
public record CollectionAccountDto(String bankCode, String accountNumber, String accountName) {

  public static CollectionAccountDto from(PlatformSettings settings) {
    return new CollectionAccountDto(
        settings.getCollectionBankCode(),
        settings.getCollectionAccountNumber(),
        settings.getCollectionAccountName());
  }
}
