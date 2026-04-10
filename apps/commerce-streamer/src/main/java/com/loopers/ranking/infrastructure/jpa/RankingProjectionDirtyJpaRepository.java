package com.loopers.ranking.infrastructure.jpa;


import com.loopers.ranking.infrastructure.entity.RankingProjectionDirtyEntity;
import com.loopers.ranking.infrastructure.entity.RankingProjectionDirtyId;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * 랭킹 projection dirty JPA 레포지토리
 */
public interface RankingProjectionDirtyJpaRepository extends JpaRepository<RankingProjectionDirtyEntity, RankingProjectionDirtyId> {
}
