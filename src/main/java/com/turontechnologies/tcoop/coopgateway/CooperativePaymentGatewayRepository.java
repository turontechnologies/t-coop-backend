package com.turontechnologies.tcoop.coopgateway;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CooperativePaymentGatewayRepository
    extends JpaRepository<CooperativePaymentGateway, String> {}
