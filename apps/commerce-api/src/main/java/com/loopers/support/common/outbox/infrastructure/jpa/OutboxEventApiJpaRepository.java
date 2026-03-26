package com.loopers.support.common.outbox.infrastructure.jpa;


import com.loopers.support.common.outbox.infrastructure.entity.OutboxEventApiEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


/**
 * Outbox 이벤트 JPA 레포지토리 (commerce-api 전용)
 */
public interface OutboxEventApiJpaRepository extends JpaRepository<OutboxEventApiEntity, Long> {

	// PENDING + FAILED(retryCount < maxRetry) 이벤트 조회 (생성 순서대로)
	@Query("SELECT e FROM OutboxEventApiEntity e " +
		"WHERE e.status IN ('PENDING', 'FAILED') AND e.retryCount < :maxRetry " +
		"ORDER BY e.createdAt ASC " +
		"LIMIT :limit")
	List<OutboxEventApiEntity> findRetryableEvents(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

}
