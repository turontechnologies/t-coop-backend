package com.turontechnologies.tcoop.loan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanPaymentIntentRepository extends JpaRepository<LoanPaymentIntent, String> {}
