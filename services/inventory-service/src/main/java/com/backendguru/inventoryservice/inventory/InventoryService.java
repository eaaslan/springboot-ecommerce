package com.backendguru.inventoryservice.inventory;

import com.backendguru.common.error.BusinessException;
import com.backendguru.common.error.ErrorCode;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.inventoryservice.inventory.dto.InventoryStatusResponse;
import com.backendguru.inventoryservice.inventory.dto.ReservationResponse;
import com.backendguru.inventoryservice.inventory.dto.ReserveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

  private final InventoryItemRepository itemRepository;
  private final ReservationRepository reservationRepository;

  @Transactional
  public ReservationResponse reserve(ReserveRequest req) {
    InventoryItem item =
        itemRepository
            .findByProductId(req.productId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Inventory not found for product " + req.productId()));
    if (item.getAvailableQty() < req.quantity()) {
      throw new InsufficientStockException(
          "Insufficient stock for product "
              + req.productId()
              + " (available "
              + item.getAvailableQty()
              + ", requested "
              + req.quantity()
              + ")");
    }
    item.setAvailableQty(item.getAvailableQty() - req.quantity());
    item.setReservedQty(item.getReservedQty() + req.quantity());
    Reservation res =
        Reservation.builder()
            .inventoryId(item.getId())
            .orderId(req.orderId())
            .quantity(req.quantity())
            .status(ReservationStatus.HELD)
            .build();
    return ReservationResponse.from(reservationRepository.save(res), item.getProductId());
  }

  @Transactional
  public void commit(Long reservationId) {
    Reservation res =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Reservation " + reservationId + " not found"));
    if (res.getStatus() != ReservationStatus.HELD) return; // idempotent
    InventoryItem item =
        itemRepository
            .findById(res.getInventoryId())
            .orElseThrow(() -> new ResourceNotFoundException("Inventory item missing"));
    item.setReservedQty(item.getReservedQty() - res.getQuantity());
    res.setStatus(ReservationStatus.COMMITTED);
  }

  @Transactional
  public void release(Long reservationId) {
    Reservation res =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Reservation " + reservationId + " not found"));
    if (res.getStatus() != ReservationStatus.HELD) return; // idempotent
    InventoryItem item =
        itemRepository
            .findById(res.getInventoryId())
            .orElseThrow(() -> new ResourceNotFoundException("Inventory item missing"));
    item.setAvailableQty(item.getAvailableQty() + res.getQuantity());
    item.setReservedQty(item.getReservedQty() - res.getQuantity());
    res.setStatus(ReservationStatus.RELEASED);
  }

  @Transactional(readOnly = true)
  public InventoryStatusResponse statusForProduct(Long productId) {
    InventoryItem item =
        itemRepository
            .findByProductId(productId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Inventory not found for product " + productId));
    return InventoryStatusResponse.from(item);
  }

  /** Concrete exception sub-typed so saga at order-service can map this distinctly to 409. */
  public static class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) {
      super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
  }
}
