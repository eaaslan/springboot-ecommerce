package com.backendguru.orderservice.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  List<OutboxEvent> findTop50ByStatusOrderByIdAsc(OutboxStatus status);
}
