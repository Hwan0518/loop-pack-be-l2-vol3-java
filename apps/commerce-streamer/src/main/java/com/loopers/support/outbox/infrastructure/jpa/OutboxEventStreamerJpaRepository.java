package com.loopers.support.outbox.infrastructure.jpa;


import com.loopers.support.outbox.infrastructure.entity.OutboxEventStreamerEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


/**
 * Outbox 이벤트 JPA 레포지토리 (commerce-streamer 전용)
 */
public interface OutboxEventStreamerJpaRepository extends JpaRepository<OutboxEventStreamerEntity, Long> {

	@Query("SELECT e FROM OutboxEventStreamerEntity e " +
		"WHERE e.status IN ('PENDING', 'FAILED') AND e.retryCount < :maxRetry " +
		"ORDER BY e.createdAt ASC")
	List<OutboxEventStreamerEntity> findRetryableEvents(@Param("maxRetry") int maxRetry, Pageable pageable);

}
