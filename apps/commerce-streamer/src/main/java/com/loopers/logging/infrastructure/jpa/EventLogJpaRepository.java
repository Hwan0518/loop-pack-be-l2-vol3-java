package com.loopers.logging.infrastructure.jpa;


import com.loopers.logging.infrastructure.entity.EventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * 이벤트 로그 JPA 레포지토리
 */
public interface EventLogJpaRepository extends JpaRepository<EventLogEntity, Long> {
}
