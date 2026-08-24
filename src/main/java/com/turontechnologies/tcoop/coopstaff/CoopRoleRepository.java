package com.turontechnologies.tcoop.coopstaff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoopRoleRepository extends JpaRepository<CoopRole, UUID> {

  List<CoopRole> findAllByCooperativeIdOrderByCreatedAtAsc(String cooperativeId);

  Optional<CoopRole> findByCooperativeIdAndNameIgnoreCase(String cooperativeId, String name);

  boolean existsByCooperativeIdAndNameIgnoreCase(String cooperativeId, String name);
}
