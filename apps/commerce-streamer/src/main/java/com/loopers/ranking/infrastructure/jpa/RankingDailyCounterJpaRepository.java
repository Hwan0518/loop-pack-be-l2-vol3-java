package com.loopers.ranking.infrastructure.jpa;


import com.loopers.ranking.infrastructure.entity.RankingDailyCounterEntity;
import com.loopers.ranking.infrastructure.entity.RankingDailyCounterId;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * 랭킹 일간 카운터 JPA 레포지토리
 * - 단순 read 용도. 배치 upsert는 JdbcTemplate 기반 별도 어댑터에서 처리.
 */
public interface RankingDailyCounterJpaRepository extends JpaRepository<RankingDailyCounterEntity, RankingDailyCounterId> {
}
