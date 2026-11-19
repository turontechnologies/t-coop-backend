package com.turontechnologies.tcoop.membership;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationGuarantorRepository extends JpaRepository<ApplicationGuarantor, UUID> {

  List<ApplicationGuarantor> findAllByApplicationId(UUID applicationId);

  List<ApplicationGuarantor> findAllByApplicationIdIn(Collection<UUID> applicationIds);

  List<ApplicationGuarantor> findAllByGuarantorMemberIdAndStatus(String guarantorMemberId, String status);

  void deleteAllByApplicationId(UUID applicationId);
}
