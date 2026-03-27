package com.loopers.readmodel.infrastructure;


import com.loopers.readmodel.application.port.out.ReadModelPort;
import com.loopers.readmodel.infrastructure.entity.StreamerProductReadModelEntity;
import com.loopers.readmodel.infrastructure.jpa.StreamerProductReadModelJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * ReadModel 동기화 포트 구현체
 * - StreamerProductReadModelJpaRepository 위임
 */

@Repository
@RequiredArgsConstructor
public class ReadModelPortImpl implements ReadModelPort {

	// jpa
	private final StreamerProductReadModelJpaRepository readModelJpaRepository;


	// 1. ReadModel 메트릭 업데이트 (version 비교 후 갱신)
	@Override
	public UpdateResult updateMetrics(Long productId, Long likeCount, Long version) {
		// ReadModel 조회
		Optional<StreamerProductReadModelEntity> readModelOpt = readModelJpaRepository.findById(productId);
		if (readModelOpt.isEmpty()) {
			return UpdateResult.NOT_FOUND;
		}

		StreamerProductReadModelEntity readModel = readModelOpt.get();

		// version 비교: incoming.version <= current.metricsVersion → skip
		if (readModel.getMetricsVersion() != null && version <= readModel.getMetricsVersion()) {
			return UpdateResult.SKIPPED_OLD_VERSION;
		}

		// ReadModel 업데이트
		readModel.updateMetrics(likeCount, version);
		readModelJpaRepository.save(readModel);

		return UpdateResult.UPDATED;
	}

}
