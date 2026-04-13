package com.loopers.ranking.application.port.out;


import java.time.LocalDate;
import java.util.Set;


/**
 * 랭킹 Redis projection 오염 마킹 포트 (application → infrastructure 계약)
 * - Redis 쓰기 실패 시 해당 날짜 + reason 으로 dirty mark
 * - reconcile job 이 이 mark 를 읽어 Redis 를 재생성한 뒤 resolved 처리
 *
 * 1. dirty mark
 */
public interface RankingProjectionDirtyPort {

	/**
	 * 영향 받은 날짜에 dirty mark 를 남긴다
	 * - 같은 (date, reason) 이면 marked_at 만 갱신 (INSERT ON DUPLICATE KEY UPDATE)
	 */
	void markDirty(Set<LocalDate> dates, String reason);

}
