package com.turontechnologies.tcoop.support;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

  List<SupportTicket> findAllByRaisedByIdOrderByCreatedAtDesc(String raisedById);

  List<SupportTicket> findAllByCooperativeIdOrderByCreatedAtDesc(String cooperativeId);

  List<SupportTicket> findAllByAssignedToRoleOrderByCreatedAtDesc(String assignedToRole);
}
