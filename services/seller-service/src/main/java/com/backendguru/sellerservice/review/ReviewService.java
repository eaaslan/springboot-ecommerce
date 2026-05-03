package com.backendguru.sellerservice.review;

import com.backendguru.common.error.ResourceNotFoundException;
import com.backendguru.sellerservice.review.dto.ReviewDtos.CreateRequest;
import com.backendguru.sellerservice.review.dto.ReviewDtos.ReviewResponse;
import com.backendguru.sellerservice.seller.Seller;
import com.backendguru.sellerservice.seller.SellerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final SellerRepository sellerRepository;

  @Transactional
  public ReviewResponse upsert(Long userId, CreateRequest req) {
    Seller seller =
        sellerRepository
            .findById(req.sellerId())
            .orElseThrow(() -> new ResourceNotFoundException("Seller " + req.sellerId()));

    Review review =
        reviewRepository
            .findByUserIdAndSellerIdAndProductId(userId, req.sellerId(), req.productId())
            .orElseGet(
                () ->
                    Review.builder()
                        .userId(userId)
                        .sellerId(req.sellerId())
                        .productId(req.productId())
                        .build());
    review.setRating(req.rating());
    review.setBody(req.body());
    review = reviewRepository.save(review);
    recomputeSellerRating(seller);
    log.info(
        "Review {} for seller={} product={} rating={} (user {})",
        review.getId(),
        review.getSellerId(),
        review.getProductId(),
        review.getRating(),
        userId);
    return ReviewResponse.from(review);
  }

  @Transactional(readOnly = true)
  public List<ReviewResponse> listForSeller(Long sellerId) {
    return reviewRepository.findBySellerIdOrderByIdDesc(sellerId).stream()
        .map(ReviewResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ReviewResponse> listForProduct(Long productId) {
    return reviewRepository.findByProductIdOrderByIdDesc(productId).stream()
        .map(ReviewResponse::from)
        .toList();
  }

  /**
   * Re-aggregates rating + ratingCount on the seller. Java-side math avoids Hibernate's varying
   * Object[] result shapes. Review counts per seller are bounded enough that this is cheap.
   */
  private void recomputeSellerRating(Seller seller) {
    List<Review> all = reviewRepository.findBySellerIdOrderByIdDesc(seller.getId());
    if (all.isEmpty()) {
      seller.setRating(BigDecimal.ZERO);
      seller.setRatingCount(0);
      return;
    }
    long sum = 0;
    for (Review r : all) sum += r.getRating();
    double avg = (double) sum / all.size();
    seller.setRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
    seller.setRatingCount(all.size());
  }
}
