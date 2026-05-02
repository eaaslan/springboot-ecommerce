package com.backendguru.productservice.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryStatusDto(Long productId, int availableQty, int reservedQty) {}
