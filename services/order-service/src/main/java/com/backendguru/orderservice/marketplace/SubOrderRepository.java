package com.backendguru.orderservice.marketplace;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubOrderRepository extends JpaRepository<SubOrder, Long> {

  List<SubOrder> findByOrderId(Long orderId);

  /** Sub-orders for one seller, newest first. */
  @Query("select s from SubOrder s where s.sellerId = ?1 order by s.id desc")
  List<SubOrder> findBySellerIdOrderByIdDesc(Long sellerId);

  /** Pending sub-orders eligible for inclusion in a weekly payout run. */
  @Query(
      "select s from SubOrder s where s.sellerId is not null and s.status = 'PENDING' "
          + "and s.payoutId is null and s.createdAt >= ?1 and s.createdAt < ?2")
  List<SubOrder> findPayablePendingInRange(OffsetDateTime start, OffsetDateTime end);
}
