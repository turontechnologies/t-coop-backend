package com.turontechnologies.tcoop.platformstaff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRoleRepository extends JpaRepository<PlatformRole, UUID> {

  List<PlatformRole> findAllByOrderByCreatedAtAsc();

  Optional<PlatformRole> findByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCase(String name);
}
