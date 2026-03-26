package com.loopers.metrics.infrastructure.jpa;


import com.loopers.metrics.infrastructure.entity.ProductMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * 상품 메트릭 JPA 레포지토리
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {
}
