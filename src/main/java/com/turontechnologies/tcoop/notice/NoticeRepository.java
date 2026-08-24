package com.turontechnologies.tcoop.notice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

  List<Notice> findAllByOrderBySendAtDesc(Pageable pageable);
}
