package com.backendguru.inventoryservice.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

  Optional<InventoryItem> findByProductId(Long productId);

  List<InventoryItem> findByProductIdIn(Collection<Long> productIds);
}
