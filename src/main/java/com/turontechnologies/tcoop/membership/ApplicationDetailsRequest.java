package com.turontechnologies.tcoop.membership;

import com.turontechnologies.tcoop.cooperative.MemberGuarantorInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Everything an applicant fills in on the form. Saved as they go; checked in full before paying. */
public record ApplicationDetailsRequest(
    @NotBlank(message = "Enter your first name") @Size(max = 100, message = "Keep your first name under 100 letters") String firstName,
    @NotBlank(message = "Enter your last name") @Size(max = 100, message = "Keep your last name under 100 letters") String lastName,
    @Size(max = 100, message = "Keep your other name under 100 letters") String otherName,
    @Pattern(regexp = "^$|Male|Female", message = "Choose Male or Female") String gender,
    LocalDate dateOfBirth,
    @NotBlank(message = "Enter your phone number") @Size(max = 30, message = "That phone number is too long") String phone,
    @Pattern(regexp = "^$|^\\d{11}$", message = "Your NIN must be exactly 11 numbers") String nin,
    @Size(max = 150, message = "Keep your job under 150 letters") String occupation,
    @Size(max = 400, message = "Keep your address under 400 letters") String homeAddress,
    @Size(max = 100) String country,
    @Size(max = 100) String state,
    @Size(max = 100) String city,
    @Size(max = 200, message = "Keep your next of kin's name under 200 letters") String nextOfKinName,
    @Size(max = 30, message = "Your next of kin's phone number is too long") String nextOfKinPhone,
    @Size(max = 50) String nextOfKinRelationship,
    @Size(max = 20) String bankCode,
    @Pattern(regexp = "^$|^\\d{10}$", message = "Your account number must be exactly 10 numbers") String accountNumber,
    @Size(max = 200) String accountName,
    @Size(max = 50) String idDocumentType,
    List<@Valid MemberGuarantorInput> guarantors) {

  /** Used only for the email check; never changes the email the code was sent to. */
  public record Start(
      @NotBlank(message = "Enter your first name") @Size(max = 100) String firstName,
      @NotBlank(message = "Enter your last name") @Size(max = 100) String lastName,
      @NotBlank(message = "Enter your email address") @Email(message = "That email address doesn't look right") @Size(max = 320) String email,
      @NotBlank(message = "Enter your phone number") @Size(max = 30) String phone) {}
}
