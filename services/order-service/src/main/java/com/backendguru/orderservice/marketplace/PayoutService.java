package com.backendguru.orderservice.marketplace;

import com.backendguru.common.error.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

  private final SubOrderRepository subOrderRepository;
  private final SellerPayoutRepository payoutRepository;

  /**
   * Aggregate every PENDING sub-order with a non-null sellerId in {@code [periodStart, periodEnd)}
   * into one payout row per seller. Idempotent on re-run because sub_orders included in a previous
   * payout have {@code payoutId} set and are excluded by the repo query.
   */
  @Transactional
  public List<SellerPayout> runForPeriod(LocalDate periodStart, LocalDate periodEnd) {
    if (!periodEnd.isAfter(periodStart)) {
      throw new ValidationException("periodEnd must be after periodStart");
    }
    OffsetDateTime startTs = periodStart.atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime endTs = periodEnd.atStartOfDay().atOffset(ZoneOffset.UTC);
    List<SubOrder> eligible = subOrderRepository.findPayablePendingInRange(startTs, endTs);
    if (eligible.isEmpty()) {
      log.info("Payout run [{}, {}) — no eligible sub-orders", periodStart, periodEnd);
      return List.of();
    }

    // Bucket by seller, keep first-seen currency per seller (would normally be one).
    Map<Long, List<SubOrder>> bySeller = new LinkedHashMap<>();
    for (SubOrder s : eligible)
      bySeller.computeIfAbsent(s.getSellerId(), k -> new ArrayList<>()).add(s);

    List<SellerPayout> created = new ArrayList<>();
    for (Map.Entry<Long, List<SubOrder>> e : bySeller.entrySet()) {
      Long sellerId = e.getKey();
      List<SubOrder> subs = e.getValue();
      BigDecimal gross = BigDecimal.ZERO;
      BigDecimal commission = BigDecimal.ZERO;
      BigDecimal net = BigDecimal.ZERO;
      String currency = subs.get(0).getCurrency();
      for (SubOrder s : subs) {
        gross = gross.add(s.getSubtotalAmount());
        commission = commission.add(s.getCommissionAmount());
        net = net.add(s.getPayoutAmount());
      }
      SellerPayout payout =
          SellerPayout.builder()
              .sellerId(sellerId)
              .periodStart(periodStart)
              .periodEnd(periodEnd)
              .grossAmount(gross)
              .commissionAmount(commission)
              .netAmount(net)
              .subOrderCount(subs.size())
              .currency(currency)
              .status("SCHEDULED")
              .build();
      payout = payoutRepository.save(payout);
      // Stamp every sub-order with the payout id (and lock it from future runs).
      for (SubOrder s : subs) {
        s.setPayoutId(payout.getId());
      }
      log.info(
          "Payout {} created — seller={} subs={} net={}",
          payout.getId(),
          sellerId,
          subs.size(),
          net);
      created.add(payout);
    }
    return created;
  }

  @Transactional
  public SellerPayout markPaid(Long payoutId) {
    SellerPayout p =
        payoutRepository
            .findById(payoutId)
            .orElseThrow(() -> new ValidationException("Payout " + payoutId + " not found"));
    p.setStatus("PAID");
    p.setPaidAt(OffsetDateTime.now());
    return p;
  }

  @Transactional(readOnly = true)
  public List<SellerPayout> listAll() {
    return payoutRepository.findAllByOrderByIdDesc();
  }

  @Transactional(readOnly = true)
  public List<SellerPayout> listForSeller(Long sellerId) {
    return payoutRepository.findBySellerIdOrderByIdDesc(sellerId);
  }
}
