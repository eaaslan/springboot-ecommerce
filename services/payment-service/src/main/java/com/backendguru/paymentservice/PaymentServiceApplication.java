package com.backendguru.paymentservice;

import com.backendguru.paymentservice.payment.iyzico.IyzicoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@EnableConfigurationProperties(IyzicoProperties.class)
@SpringBootApplication(
    scanBasePackages = {"com.backendguru.paymentservice", "com.backendguru.common"})
public class PaymentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentServiceApplication.class, args);
  }
}
