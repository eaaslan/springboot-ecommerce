package com.backendguru.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableCaching
@EnableDiscoveryClient
@SpringBootApplication(
    scanBasePackages = {"com.backendguru.productservice", "com.backendguru.common"})
public class ProductServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProductServiceApplication.class, args);
  }
}
