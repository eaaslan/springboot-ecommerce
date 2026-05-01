package com.backendguru.productservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false"
    })
class ProductFlowIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mockMvc;

  @Test
  void publicListReturnsPaginatedResponse() throws Exception {
    mockMvc
        .perform(get("/api/products?page=0&size=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(5))
        .andExpect(jsonPath("$.data.size").value(5))
        .andExpect(jsonPath("$.data.totalElements").value(20));
  }

  @Test
  void filterByCategoryReducesResults() throws Exception {
    mockMvc
        .perform(get("/api/products?categoryId=1&size=50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].category.slug").value("electronics"));
  }

  @Test
  void getByIdReturnsProductWithCategory() throws Exception {
    mockMvc
        .perform(get("/api/products/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.category.name").exists());
  }

  @Test
  void categoriesEndpointReturnsAll() throws Exception {
    mockMvc
        .perform(get("/api/products/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(5));
  }

  @Test
  void anonymousCannotCreate() throws Exception {
    mockMvc
        .perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminGetsForbidden() throws Exception {
    String body =
        """
        {"sku":"SKU-X","name":"X","priceAmount":1.00,"priceCurrency":"TRY",
         "stockQuantity":1,"categoryId":1}
        """;
    mockMvc
        .perform(
            post("/api/products")
                .header("X-User-Id", "1")
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateAndDelete() throws Exception {
    String body =
        """
        {"sku":"SKU-NEW-001","name":"New Item","priceAmount":99.99,"priceCurrency":"TRY",
         "stockQuantity":5,"categoryId":1}
        """;
    String created =
        mockMvc
            .perform(
                post("/api/products")
                    .header("X-User-Id", "999")
                    .header("X-User-Role", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.sku").value("SKU-NEW-001"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Integer id = com.jayway.jsonpath.JsonPath.read(created, "$.data.id");
    mockMvc
        .perform(
            delete("/api/products/" + id).header("X-User-Id", "999").header("X-User-Role", "ADMIN"))
        .andExpect(status().isNoContent());
  }

  @Test
  void duplicateSkuReturnsConflict() throws Exception {
    String body =
        """
        {"sku":"SKU-EL-001","name":"Dup","priceAmount":1.00,"priceCurrency":"TRY",
         "stockQuantity":1,"categoryId":1}
        """;
    mockMvc
        .perform(
            post("/api/products")
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
  }
}
