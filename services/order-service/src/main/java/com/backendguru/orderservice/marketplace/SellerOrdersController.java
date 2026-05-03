package com.backendguru.orderservice.marketplace;

import com.backendguru.common.dto.ApiResponse;
import com.backendguru.common.error.ForbiddenException;
import com.backendguru.common.error.UnauthorizedException;
import com.backendguru.orderservice.marketplace.dto.SubOrderResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller-orders")
@RequiredArgsConstructor
public class SellerOrdersController {

  private final SubOrderRepository subOrderRepository;
  private final SellerProfileClient sellerProfileClient;

  /** All sub-orders the current seller has to fulfil. */
  @GetMapping("/me")
  public ApiResponse<List<SubOrderResponse>> mySubOrders(Authentication auth) {
    requireUser(auth);
    var resp = sellerProfileClient.me();
    var profile = resp == null ? null : resp.data();
    if (profile == null || profile.id() == null) {
      throw new ForbiddenException("Seller record required to view incoming orders");
    }
    if (!"ACTIVE".equalsIgnoreCase(profile.status())) {
      throw new ForbiddenException("Seller account is not active");
    }
    List<SubOrderResponse> subs =
        subOrderRepository.findBySellerIdOrderByIdDesc(profile.id()).stream()
            .map(SubOrderResponse::from)
            .toList();
    return ApiResponse.success(subs);
  }

  private static void requireUser(Authentication auth) {
    if (auth == null || !(auth.getPrincipal() instanceof Long)) {
      throw new UnauthorizedException("Authentication required");
    }
  }
}
