package com.loopers.readmodel.infrastructure.jpa;


import com.loopers.readmodel.infrastructure.entity.StreamerProductReadModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * ReadModel 동기화용 JPA 레포지토리
 */
public interface StreamerProductReadModelJpaRepository extends JpaRepository<StreamerProductReadModelEntity, Long> {
}
