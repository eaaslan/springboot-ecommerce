package com.backendguru.sellerservice.seller.dto;

import com.backendguru.sellerservice.seller.Seller;
import com.backendguru.sellerservice.seller.SellerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class SellerDtos {

  private SellerDtos() {}

  public record ApplyRequest(
      @NotBlank @Size(max = 200) String businessName,
      @Size(max = 40) String taxId,
      @Size(max = 40) String iban,
      @Email @Size(max = 120) String contactEmail,
      @Size(max = 40) String contactPhone) {}

  public record UpdateProfileRequest(
      String businessName, String taxId, String iban, String contactEmail, String contactPhone) {}

  /** Admin actions: approve / suspend / re-activate. */
  public record AdminUpdateRequest(SellerStatus status, BigDecimal commissionPct) {}

  public record SellerResponse(
      Long id,
      Long userId,
      String businessName,
      String taxId,
      String iban,
      String contactEmail,
      String contactPhone,
      BigDecimal commissionPct,
      SellerStatus status,
      BigDecimal rating,
      int ratingCount,
      OffsetDateTime createdAt,
      OffsetDateTime approvedAt) {

    public static SellerResponse from(Seller s) {
      return new SellerResponse(
          s.getId(),
          s.getUserId(),
          s.getBusinessName(),
          s.getTaxId(),
          s.getIban(),
          s.getContactEmail(),
          s.getContactPhone(),
          s.getCommissionPct(),
          s.getStatus(),
          s.getRating(),
          s.getRatingCount(),
          s.getCreatedAt(),
          s.getApprovedAt());
    }
  }

  /** Public storefront — only safe fields, no IBAN/tax. */
  public record SellerPublicResponse(
      Long id, String businessName, BigDecimal rating, int ratingCount) {

    public static SellerPublicResponse from(Seller s) {
      return new SellerPublicResponse(
          s.getId(), s.getBusinessName(), s.getRating(), s.getRatingCount());
    }
  }
}
