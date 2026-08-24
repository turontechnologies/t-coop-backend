package com.turontechnologies.tcoop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TCoopBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(TCoopBackendApplication.class, args);
  }
}
