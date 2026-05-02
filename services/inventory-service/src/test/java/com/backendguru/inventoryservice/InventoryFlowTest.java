package com.backendguru.inventoryservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backendguru.inventoryservice.inventory.InventoryItemRepository;
import com.backendguru.inventoryservice.inventory.InventoryService;
import com.backendguru.inventoryservice.inventory.ReservationStatus;
import com.backendguru.inventoryservice.inventory.dto.ReserveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=",
      "eureka.client.enabled=false"
    })
class InventoryFlowTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired InventoryService service;
  @Autowired InventoryItemRepository itemRepo;

  @Test
  void seedDataLoadsViaFlyway() {
    assertThat(itemRepo.count()).isEqualTo(20);
    // Use a product no other test touches to avoid side-effect coupling.
    assertThat(itemRepo.findByProductId(7L).orElseThrow().getAvailableQty()).isEqualTo(35);
  }

  @Test
  void reserveDecrementsAvailableAndIncrementsReserved() {
    var resp = service.reserve(new ReserveRequest(1L, 5, 1000L));

    assertThat(resp.status()).isEqualTo(ReservationStatus.HELD);
    assertThat(resp.quantity()).isEqualTo(5);
    var item = itemRepo.findByProductId(1L).orElseThrow();
    assertThat(item.getAvailableQty()).isEqualTo(45);
    assertThat(item.getReservedQty()).isEqualTo(5);
  }

  @Test
  void reserveFailsOnInsufficientStock() {
    assertThatThrownBy(() -> service.reserve(new ReserveRequest(2L, 9999, 1001L)))
        .isInstanceOf(InventoryService.InsufficientStockException.class);

    var item = itemRepo.findByProductId(2L).orElseThrow();
    assertThat(item.getAvailableQty()).isEqualTo(25); // unchanged
    assertThat(item.getReservedQty()).isEqualTo(0);
  }

  @Test
  void commitClearsReservedQuantityFromItem() {
    var resp = service.reserve(new ReserveRequest(3L, 4, 1002L));
    service.commit(resp.reservationId());

    var item = itemRepo.findByProductId(3L).orElseThrow();
    assertThat(item.getAvailableQty()).isEqualTo(76); // 80 - 4
    assertThat(item.getReservedQty()).isEqualTo(0); // 4 - 4 (committed → no longer reserved, gone)
  }

  @Test
  void releaseReturnsQuantityToAvailable() {
    var resp = service.reserve(new ReserveRequest(4L, 6, 1003L));
    service.release(resp.reservationId());

    var item = itemRepo.findByProductId(4L).orElseThrow();
    assertThat(item.getAvailableQty()).isEqualTo(120); // 120 - 6 + 6
    assertThat(item.getReservedQty()).isEqualTo(0);
  }

  @Test
  void commitOrReleaseIsIdempotent() {
    var resp = service.reserve(new ReserveRequest(5L, 2, 1004L));
    service.release(resp.reservationId());
    service.release(resp.reservationId()); // second call no-op

    var item = itemRepo.findByProductId(5L).orElseThrow();
    assertThat(item.getAvailableQty()).isEqualTo(60); // back to seed
    assertThat(item.getReservedQty()).isEqualTo(0);
  }

  @Test
  void statusReturnsCurrentInventory() {
    var status = service.statusForProduct(6L);
    assertThat(status.availableQty()).isEqualTo(40);
    assertThat(status.reservedQty()).isEqualTo(0);
  }
}
