package com.loopers.readmodel.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * ReadModel 동기화용 경량 엔티티 (product_read_model)
 * - ReadModelSyncConsumer가 like_count + metrics_version만 UPDATE
 */

@Entity
@Table(name = "product_read_model")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamerProductReadModelEntity {

	@Id
	private Long id;

	@Column(name = "like_count")
	private Long likeCount;

	@Column(name = "metrics_version")
	private Long metricsVersion;


	// 메트릭 업데이트 (version 비교 후 호출)
	public void updateMetrics(Long likeCount, Long metricsVersion) {
		this.likeCount = likeCount;
		this.metricsVersion = metricsVersion;
	}

}
