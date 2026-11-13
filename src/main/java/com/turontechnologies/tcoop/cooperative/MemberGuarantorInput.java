package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.Size;

/**
 * One guarantor on Add/Edit Member. A member of the co-op is sent by {@code memberId}; someone
 * from outside the co-op is sent with {@code name} and {@code phone} (email optional).
 */
public record MemberGuarantorInput(
    @Size(max = 20) String memberId,
    @Size(max = 200, message = "Keep the guarantor's name under 200 characters") String name,
    @Size(max = 30, message = "Keep the guarantor's phone under 30 characters") String phone,
    @Size(max = 255, message = "Keep the guarantor's email under 255 characters") String email) {

  boolean fromCoop() {
    return memberId != null && !memberId.isBlank();
  }
}
