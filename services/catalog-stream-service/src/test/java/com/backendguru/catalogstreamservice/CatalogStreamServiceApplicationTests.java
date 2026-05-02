package com.backendguru.catalogstreamservice;

import com.backendguru.catalogstreamservice.catalog.ProductReactiveRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1",
      "spring.r2dbc.username=sa",
      "spring.r2dbc.password="
    })
class CatalogStreamServiceApplicationTests {

  @MockBean ProductReactiveRepository repository;

  @Test
  void contextLoads() {}
}
