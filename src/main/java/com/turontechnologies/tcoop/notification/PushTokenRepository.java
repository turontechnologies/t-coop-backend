package com.turontechnologies.tcoop.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

  Optional<PushToken> findByToken(String token);

  List<PushToken> findAllByMemberId(String memberId);
}
