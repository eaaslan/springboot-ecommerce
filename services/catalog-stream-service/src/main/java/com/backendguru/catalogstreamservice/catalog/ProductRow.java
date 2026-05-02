package com.backendguru.catalogstreamservice.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC row binding for the {@code products} table owned by product-service. We're a read-only
 * consumer; no writes happen through this module.
 */
@Table("products")
public record ProductRow(
    @Id Long id,
    String sku,
    String name,
    String description,
    @Column("image_url") String imageUrl,
    @Column("price_amount") BigDecimal priceAmount,
    @Column("price_currency") String priceCurrency,
    @Column("stock_quantity") int stockQuantity,
    @Column("category_id") Long categoryId,
    boolean enabled,
    @Column("created_at") OffsetDateTime createdAt,
    @Column("updated_at") OffsetDateTime updatedAt,
    long version) {}
