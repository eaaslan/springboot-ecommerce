package com.backendguru.sellerservice.listing;

import com.backendguru.common.error.DuplicateResourceException;
import com.backendguru.common.error.ForbiddenException;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import com.backendguru.sellerservice.listing.dto.ListingDtos.CreateRequest;
import com.backendguru.sellerservice.listing.dto.ListingDtos.ListingResponse;
import com.backendguru.sellerservice.listing.dto.ListingDtos.UpdateRequest;
import com.backendguru.sellerservice.seller.Seller;
import com.backendguru.sellerservice.seller.SellerRepository;
import com.backendguru.sellerservice.seller.SellerService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingService {

  private final ListingRepository listingRepository;
  private final SellerRepository sellerRepository;
  private final SellerService sellerService;

  // -------- public read paths (for catalog enrichment) --------

  @Transactional(readOnly = true)
  public ListingResponse publicById(Long id) {
    Listing l =
        listingRepository
            .findById(id)
            .filter(Listing::isEnabled)
            .orElseThrow(() -> new ResourceNotFoundException("Listing " + id + " not found"));
    String sellerName =
        sellerRepository.findById(l.getSellerId()).map(Seller::getBusinessName).orElse(null);
    return ListingResponse.from(l, sellerName);
  }

  @Transactional(readOnly = true)
  public List<ListingResponse> publicForProduct(Long productId) {
    List<Listing> listings = listingRepository.findByProductIdAndEnabledTrue(productId);
    Map<Long, String> names = sellerNamesFor(listings);
    return listings.stream().map(l -> ListingResponse.from(l, names.get(l.getSellerId()))).toList();
  }

  @Transactional(readOnly = true)
  public Map<Long, ListingResponse> bestListingsForProducts(List<Long> productIds) {
    if (productIds == null || productIds.isEmpty()) return Map.of();
    List<Listing> all = listingRepository.findByProductIdInAndEnabledTrue(productIds);
    Map<Long, String> names = sellerNamesFor(all);
    Map<Long, ListingResponse> best = new HashMap<>();
    for (Listing l : all) {
      if (l.getStockQuantity() <= 0) continue;
      ListingResponse current = best.get(l.getProductId());
      if (current == null || score(l) < scoreOf(current)) {
        best.put(l.getProductId(), ListingResponse.from(l, names.get(l.getSellerId())));
      }
    }
    return best;
  }

  // -------- seller (owner) write paths --------

  @Transactional
  public ListingResponse createForUser(Long userId, CreateRequest req) {
    Seller seller =
        sellerService
            .findActiveByUserId(userId)
            .orElseThrow(() -> new ForbiddenException("Active seller record required"));
    Listing l =
        Listing.builder()
            .productId(req.productId())
            .sellerId(seller.getId())
            .priceAmount(req.priceAmount())
            .priceCurrency(req.priceCurrency() == null ? "TRY" : req.priceCurrency().toUpperCase())
            .stockQuantity(req.stockQuantity() == null ? 0 : req.stockQuantity())
            .condition(req.condition() == null ? ListingCondition.NEW : req.condition())
            .shippingDays(req.shippingDays() == null ? 2 : req.shippingDays())
            .enabled(true)
            .build();
    try {
      l = listingRepository.save(l);
    } catch (org.springframework.dao.DataIntegrityViolationException dup) {
      throw new DuplicateResourceException(
          "You already have a listing for product "
              + req.productId()
              + " with condition "
              + l.getCondition());
    }
    log.info(
        "Listing created: id={} productId={} sellerId={} price={}",
        l.getId(),
        l.getProductId(),
        seller.getId(),
        l.getPriceAmount());
    return ListingResponse.from(l, seller.getBusinessName());
  }

  @Transactional
  public ListingResponse updateForUser(Long userId, Long listingId, UpdateRequest req) {
    Listing l = loadAndAuthorize(userId, listingId);
    if (req.priceAmount() != null) {
      if (req.priceAmount().signum() <= 0)
        throw new ValidationException("priceAmount must be positive");
      l.setPriceAmount(req.priceAmount());
    }
    if (req.priceCurrency() != null) l.setPriceCurrency(req.priceCurrency().toUpperCase());
    if (req.stockQuantity() != null) {
      if (req.stockQuantity() < 0) throw new ValidationException("stockQuantity must be ≥ 0");
      l.setStockQuantity(req.stockQuantity());
    }
    if (req.shippingDays() != null) l.setShippingDays(req.shippingDays());
    if (req.enabled() != null) l.setEnabled(req.enabled());
    Seller seller =
        sellerRepository
            .findById(l.getSellerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Seller " + l.getSellerId() + " gone"));
    return ListingResponse.from(l, seller.getBusinessName());
  }

  @Transactional
  public void disableForUser(Long userId, Long listingId) {
    Listing l = loadAndAuthorize(userId, listingId);
    l.setEnabled(false);
  }

  @Transactional(readOnly = true)
  public List<ListingResponse> listForUser(Long userId) {
    Seller seller =
        sellerService
            .findActiveByUserId(userId)
            .orElseThrow(() -> new ForbiddenException("Active seller record required"));
    List<Listing> rows = listingRepository.findBySellerId(seller.getId());
    return rows.stream().map(l -> ListingResponse.from(l, seller.getBusinessName())).toList();
  }

  // -------- internals --------

  private Listing loadAndAuthorize(Long userId, Long listingId) {
    Listing l =
        listingRepository
            .findById(listingId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Listing " + listingId + " not found"));
    Seller seller =
        sellerService
            .findActiveByUserId(userId)
            .orElseThrow(() -> new ForbiddenException("Active seller record required"));
    if (!l.getSellerId().equals(seller.getId())) {
      throw new ForbiddenException("Listing does not belong to your seller account");
    }
    return l;
  }

  private Map<Long, String> sellerNamesFor(List<Listing> listings) {
    if (listings.isEmpty()) return Map.of();
    var sellerIds = listings.stream().map(Listing::getSellerId).distinct().toList();
    Map<Long, String> map = new HashMap<>();
    sellerRepository.findAllById(sellerIds).forEach(s -> map.put(s.getId(), s.getBusinessName()));
    return map;
  }

  /** buy-box score: lower is better. price + shipping_days × 5 (5 TRY/day shipping speed proxy). */
  private static double score(Listing l) {
    return l.getPriceAmount().doubleValue() + (l.getShippingDays() * 5.0);
  }

  private static double scoreOf(ListingResponse r) {
    return r.priceAmount().doubleValue() + (r.shippingDays() * 5.0);
  }
}
