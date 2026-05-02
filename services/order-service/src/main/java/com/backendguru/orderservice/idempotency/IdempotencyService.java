package com.backendguru.orderservice.idempotency;

import com.backendguru.orderservice.order.dto.OrderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures and replays idempotent POST /api/orders responses.
 *
 * <p>Composite primary key {@code (idempotency_key, user_id)} prevents key leakage between users.
 * Concurrent same-key inserts race on the PK constraint; loser catches {@link
 * DataIntegrityViolationException} and re-reads the winner's row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

  private final ProcessedOrderRepository repository;
  private final ObjectMapper objectMapper;

  public Optional<ProcessedOrder> lookup(String key, Long userId) {
    if (key == null || key.isBlank()) return Optional.empty();
    return repository.findByIdempotencyKeyAndUserId(key, userId);
  }

  /** Best-effort capture; never bubbles errors to the caller (the order is already created). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void capture(String key, Long userId, OrderResponse response, int status) {
    if (key == null || key.isBlank()) return;
    try {
      ProcessedOrder row =
          new ProcessedOrder(
              key,
              userId,
              response.id(),
              objectMapper.writeValueAsString(response),
              status,
              null);
      repository.save(row);
    } catch (DataIntegrityViolationException dup) {
      log.info("Idempotency capture race: key={}, user={} already stored", key, userId);
    } catch (JsonProcessingException ex) {
      log.warn("Idempotency capture: failed to serialize response for order {}", response.id(), ex);
    }
  }

  public OrderResponse deserialize(ProcessedOrder row) throws JsonProcessingException {
    return objectMapper.readValue(row.getResponseBody(), OrderResponse.class);
  }
}
