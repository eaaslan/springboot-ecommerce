package com.backendguru.inventoryservice.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  List<Reservation> findByOrderId(Long orderId);
}
