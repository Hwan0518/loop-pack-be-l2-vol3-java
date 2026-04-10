package com.loopers.ranking.infrastructure.jpa;


import com.loopers.ranking.infrastructure.entity.RankingDailyScoreEntity;
import com.loopers.ranking.infrastructure.entity.RankingDailyScoreId;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * 랭킹 일간 점수 스냅샷 JPA 레포지토리
 * - 단순 read 용도. organic/carry 갱신은 JdbcTemplate 기반 별도 어댑터에서 처리.
 */
public interface RankingDailyScoreJpaRepository extends JpaRepository<RankingDailyScoreEntity, RankingDailyScoreId> {
}
