package com.backendguru.cartservice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

@SpringBootTest
@AutoConfigureMockMvc
@EnableWireMock
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.cloud.openfeign.client.config.product-service.url=${wiremock.server.baseUrl}",
      "spring.cloud.openfeign.circuitbreaker.enabled=true",
      "resilience4j.circuitbreaker.instances.productClient.minimum-number-of-calls=2",
      "resilience4j.circuitbreaker.instances.productClient.sliding-window-size=2",
      "resilience4j.retry.instances.productClient.max-attempts=2",
      "resilience4j.retry.instances.productClient.wait-duration=10ms",
      "resilience4j.timelimiter.instances.productClient.timeout-duration=2s"
    })
class CartFlowIntegrationTest {

  @Autowired MockMvc mockMvc;

  @InjectWireMock WireMockServer wm;

  private void stubProduct(long id, int stock, boolean enabled, double price) {
    wm.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/" + id))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "success": true,
                          "data": {
                            "id": %d,
                            "name": "Widget",
                            "priceAmount": %.2f,
                            "priceCurrency": "TRY",
                            "stockQuantity": %d,
                            "enabled": %b
                          },
                          "timestamp": "2026-05-01T10:00:00Z"
                        }
                        """
                            .formatted(id, price, stock, enabled))));
  }

  @Test
  void anonymousRequestReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedUserCanAddItem() throws Exception {
    stubProduct(1L, 50, true, 10.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "42")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(42))
        .andExpect(jsonPath("$.data.items[0].productId").value(1))
        .andExpect(jsonPath("$.data.items[0].quantity").value(2));
  }

  @Test
  void mergesQuantityWhenSameProductAddedTwice() throws Exception {
    stubProduct(2L, 50, true, 5.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "100")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":2,\"quantity\":1}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "100")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":2,\"quantity\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].quantity").value(4));
  }

  @Test
  void productServerErrorTriggersFallback503() throws Exception {
    wm.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/99"))
            .willReturn(aResponse().withStatus(500).withBody("oops")));

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "200")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":99,\"quantity\":1}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "201")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":99,\"quantity\":1}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void disabledProductRejectedWithValidation() throws Exception {
    stubProduct(3L, 50, false, 10.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "300")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":3,\"quantity\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void differentUsersHaveIndependentCarts() throws Exception {
    stubProduct(4L, 50, true, 10.00);

    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "400")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":4,\"quantity\":1}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/cart").header("X-User-Id", "401").header("X-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }

  @Test
  void deleteCartClearsItems() throws Exception {
    stubProduct(5L, 50, true, 10.00);
    mockMvc
        .perform(
            post("/api/cart/items")
                .header("X-User-Id", "500")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":5,\"quantity\":1}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/cart").header("X-User-Id", "500").header("X-User-Role", "USER"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/cart").header("X-User-Id", "500").header("X-User-Role", "USER"))
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }
}
