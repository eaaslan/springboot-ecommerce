package com.backendguru.sellerservice.seller;

import com.backendguru.common.error.DuplicateResourceException;
import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.common.error.ValidationException;
import com.backendguru.sellerservice.seller.dto.SellerDtos.AdminUpdateRequest;
import com.backendguru.sellerservice.seller.dto.SellerDtos.ApplyRequest;
import com.backendguru.sellerservice.seller.dto.SellerDtos.SellerPublicResponse;
import com.backendguru.sellerservice.seller.dto.SellerDtos.SellerResponse;
import com.backendguru.sellerservice.seller.dto.SellerDtos.UpdateProfileRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerService {

  private final SellerRepository repository;

  // -------- user-facing --------

  @Transactional
  public SellerResponse apply(Long userId, ApplyRequest req) {
    repository
        .findByUserId(userId)
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException(
                  "User already has a seller record with status " + existing.getStatus());
            });
    Seller s =
        Seller.builder()
            .userId(userId)
            .businessName(req.businessName())
            .taxId(req.taxId())
            .iban(req.iban())
            .contactEmail(req.contactEmail())
            .contactPhone(req.contactPhone())
            .status(SellerStatus.PENDING)
            .build();
    s = repository.save(s);
    log.info("Seller application received: userId={} sellerId={}", userId, s.getId());
    return SellerResponse.from(s);
  }

  @Transactional(readOnly = true)
  public Optional<SellerResponse> findByUserId(Long userId) {
    return repository.findByUserId(userId).map(SellerResponse::from);
  }

  /** True if the user has an ACTIVE seller record. Used for listing-write authorization. */
  @Transactional(readOnly = true)
  public Optional<Seller> findActiveByUserId(Long userId) {
    return repository.findByUserId(userId).filter(s -> s.getStatus() == SellerStatus.ACTIVE);
  }

  @Transactional
  public SellerResponse updateProfile(Long userId, UpdateProfileRequest req) {
    Seller s =
        repository
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No seller record for current user"));
    if (req.businessName() != null) s.setBusinessName(req.businessName());
    if (req.taxId() != null) s.setTaxId(req.taxId());
    if (req.iban() != null) s.setIban(req.iban());
    if (req.contactEmail() != null) s.setContactEmail(req.contactEmail());
    if (req.contactPhone() != null) s.setContactPhone(req.contactPhone());
    return SellerResponse.from(s);
  }

  // -------- public storefront --------

  @Transactional(readOnly = true)
  public SellerPublicResponse publicById(Long id) {
    return SellerPublicResponse.from(loadOrThrow(id));
  }

  // -------- admin --------

  @Transactional(readOnly = true)
  public List<SellerResponse> listForAdmin(SellerStatus status) {
    List<Seller> rows = status == null ? repository.findAll() : repository.findByStatus(status);
    return rows.stream().map(SellerResponse::from).toList();
  }

  @Transactional
  public SellerResponse adminUpdate(Long id, AdminUpdateRequest req) {
    Seller s = loadOrThrow(id);
    if (req.status() != null) {
      transitionStatus(s, req.status());
    }
    if (req.commissionPct() != null) {
      if (req.commissionPct().signum() < 0 || req.commissionPct().intValue() > 50) {
        throw new ValidationException("commissionPct must be between 0 and 50");
      }
      s.setCommissionPct(req.commissionPct());
    }
    return SellerResponse.from(s);
  }

  private void transitionStatus(Seller s, SellerStatus target) {
    if (s.getStatus() == target) return;
    if (target == SellerStatus.ACTIVE && s.getApprovedAt() == null) {
      s.setApprovedAt(OffsetDateTime.now());
    }
    log.info("Seller {} status: {} → {}", s.getId(), s.getStatus(), target);
    s.setStatus(target);
  }

  private Seller loadOrThrow(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Seller " + id + " not found"));
  }
}
