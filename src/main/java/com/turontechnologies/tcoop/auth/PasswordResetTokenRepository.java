package com.turontechnologies.tcoop.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, java.util.UUID> {

  Optional<PasswordResetToken> findFirstByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
      String email, LocalDateTime now);

  Optional<PasswordResetToken> findFirstByResetTokenAndUsedFalseAndExpiresAtAfter(
      String resetToken, LocalDateTime now);
}
