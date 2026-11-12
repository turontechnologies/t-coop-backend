package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request shapes for the borrower's answer to an approved loan, and for paying it out. */
public final class LoanOfferRequests {

  private LoanOfferRequests() {}

  /**
   * The borrower accepts or declines the approved terms. Accepting says where to pay the money:
   * their registered account, or another account that must be in their own registered name.
   */
  public record OfferResponse(
      @NotBlank @Pattern(regexp = "Accepted|Declined", message = "Accept or decline the offer") String decision,
      @Pattern(regexp = "Registered|Other", message = "Choose your registered account or another account")
          String accountType,
      String bankCode,
      @Size(max = 100) String bankName,
      @Pattern(regexp = "\\d{10}", message = "Account numbers have 10 digits") String accountNumber,
      /** What the member typed — only used when the bank can't be asked for the real name. */
      @Size(max = 200) String accountName,
      @Size(max = 1000) String reason) {}

  public record Disburse(
      @NotBlank @Pattern(regexp = "Online|Cheque", message = "Choose online or cheque") String method,
      @Size(max = 500) String chequeUrl,
      @Size(max = 30) String chequeNumber) {}
}
