package com.backendguru.inventoryservice.inventory.dto;

import com.backendguru.inventoryservice.inventory.InventoryItem;

public record InventoryStatusResponse(Long productId, int availableQty, int reservedQty) {

  public static InventoryStatusResponse from(InventoryItem item) {
    return new InventoryStatusResponse(
        item.getProductId(), item.getAvailableQty(), item.getReservedQty());
  }
}
