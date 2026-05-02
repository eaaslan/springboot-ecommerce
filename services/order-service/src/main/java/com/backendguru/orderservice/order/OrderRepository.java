package com.backendguru.orderservice.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByUserIdOrderByIdDesc(Long userId);

  @EntityGraph(attributePaths = "items")
  Optional<Order> findWithItemsById(Long id);
}
