package com.backendguru.sellerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {"com.backendguru.sellerservice", "com.backendguru.common"})
public class SellerServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SellerServiceApplication.class, args);
  }
}
