package com.backendguru.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerNativeBackendTest {

  @LocalServerPort int port;

  @Autowired TestRestTemplate rest;

  @Test
  void servesApiGatewayDevConfig() {
    var response = rest.getForObject("http://localhost:" + port + "/api-gateway/dev", String.class);
    assertThat(response).contains("api-gateway");
    assertThat(response).contains("8080");
  }

  @Test
  void servesDiscoveryServerConfig() {
    var response =
        rest.getForObject("http://localhost:" + port + "/discovery-server/default", String.class);
    assertThat(response).contains("8761");
  }
}
