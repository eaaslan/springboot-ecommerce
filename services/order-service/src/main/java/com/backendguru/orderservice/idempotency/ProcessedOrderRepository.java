package com.backendguru.orderservice.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, ProcessedOrder.PK> {

  Optional<ProcessedOrder> findByIdempotencyKeyAndUserId(String key, Long userId);
}
