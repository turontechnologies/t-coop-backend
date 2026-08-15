package com.turontechnologies.tcoop.cooperative;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CooperativeRepository extends JpaRepository<Cooperative, String> {

  long countByStatus(String status);

  List<Cooperative> findAllByOrderByNameAsc();
}
