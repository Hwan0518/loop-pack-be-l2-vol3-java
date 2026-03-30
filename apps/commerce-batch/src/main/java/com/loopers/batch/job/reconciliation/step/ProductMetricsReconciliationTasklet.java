package com.loopers.batch.job.reconciliation.step;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


/**
 * product_metrics vs product_read_model drift 보정 Tasklet
 * - product_metrics.like_count와 product_read_model.like_count 불일치 감지
 * - 불일치 발견 시 product_read_model을 product_metrics 기준으로 보정
 * - metrics_version도 함께 보정
 */

@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductMetricsReconciliationTasklet implements Tasklet {

	// jdbc
	private final JdbcTemplate jdbcTemplate;


	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

		// product_metrics와 product_read_model의 like_count 불일치 보정
		int updated = jdbcTemplate.update(
			"UPDATE product_read_model rm " +
			"INNER JOIN product_metrics pm ON rm.id = pm.product_id " +
			"SET rm.like_count = pm.like_count, rm.metrics_version = pm.version " +
			"WHERE rm.like_count != pm.like_count OR rm.metrics_version != pm.version"
		);

		if (updated > 0) {
			log.info("[Reconciliation] product_read_model 보정 완료: {}건", updated);
		} else {
			log.info("[Reconciliation] drift 없음 — 정상");
		}

		contribution.incrementWriteCount(updated);

		return RepeatStatus.FINISHED;
	}

}
