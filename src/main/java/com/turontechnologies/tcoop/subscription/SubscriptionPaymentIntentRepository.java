package com.turontechnologies.tcoop.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentIntentRepository
    extends JpaRepository<SubscriptionPaymentIntent, String> {}
