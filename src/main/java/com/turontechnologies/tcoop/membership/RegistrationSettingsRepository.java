package com.turontechnologies.tcoop.membership;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSettingsRepository extends JpaRepository<RegistrationSettings, String> {

  List<RegistrationSettings> findAllByOpenTrue();
}
