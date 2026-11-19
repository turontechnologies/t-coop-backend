package com.turontechnologies.tcoop.membership;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipApplicationRepository extends JpaRepository<MembershipApplication, UUID> {

  Optional<MembershipApplication> findByReference(String reference);

  List<MembershipApplication> findAllByCooperativeIdAndStatusInOrderBySubmittedAtDesc(
      String cooperativeId, Collection<String> statuses);

  List<MembershipApplication> findAllByCooperativeIdAndEmailOrderByCreatedAtDesc(String cooperativeId, String email);

  List<MembershipApplication> findAllByCooperativeIdAndNinOrderByCreatedAtDesc(String cooperativeId, String nin);

  List<MembershipApplication> findAllByCooperativeIdAndPhoneOrderByCreatedAtDesc(String cooperativeId, String phone);

  long countByCooperativeIdAndStatus(String cooperativeId, String status);
}
