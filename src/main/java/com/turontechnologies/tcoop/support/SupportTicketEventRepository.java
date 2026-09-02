package com.turontechnologies.tcoop.support;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketEventRepository extends JpaRepository<SupportTicketEvent, UUID> {

  List<SupportTicketEvent> findAllByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
