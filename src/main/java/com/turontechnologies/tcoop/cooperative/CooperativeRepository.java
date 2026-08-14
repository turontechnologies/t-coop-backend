package com.turontechnologies.tcoop.cooperative;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CooperativeRepository extends JpaRepository<Cooperative, String> {

  long countByStatus(String status);
}
