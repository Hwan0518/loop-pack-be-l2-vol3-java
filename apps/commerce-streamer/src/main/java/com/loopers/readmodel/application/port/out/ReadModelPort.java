package com.loopers.readmodel.application.port.out;


/**
 * ReadModel 동기화 포트 (application → infrastructure 계약)
 * - snapshot 이벤트로부터 ReadModel 업데이트
 */
public interface ReadModelPort {

	/**
	 * ReadModel 메트릭 업데이트 (version 비교 후 갱신)
	 * @return 업데이트 결과 (NOT_FOUND, SKIPPED_OLD_VERSION, UPDATED)
	 */
	UpdateResult updateMetrics(Long productId, Long likeCount, Long version);


	/**
	 * 업데이트 결과 enum
	 */
	enum UpdateResult {
		NOT_FOUND,
		SKIPPED_OLD_VERSION,
		UPDATED
	}

}
