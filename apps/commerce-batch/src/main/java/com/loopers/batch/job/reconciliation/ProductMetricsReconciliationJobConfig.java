package com.loopers.batch.job.reconciliation;


import com.loopers.batch.job.reconciliation.step.ProductMetricsReconciliationTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;


/**
 * product_metrics vs product_read_model drift 보정 Job (안전망)
 * - 하루 1회 실행 (또는 수동 트리거)
 * - product_metrics의 like_count와 product_read_model의 like_count 불일치 감지 + 보정
 */

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductMetricsReconciliationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductMetricsReconciliationJobConfig {

	public static final String JOB_NAME = "productMetricsReconciliationJob";
	private static final String STEP_NAME = "productMetricsReconciliationStep";

	private final JobRepository jobRepository;
	private final JobListener jobListener;
	private final StepMonitorListener stepMonitorListener;
	private final ProductMetricsReconciliationTasklet tasklet;
	private final PlatformTransactionManager transactionManager;


	@Bean(JOB_NAME)
	public Job productMetricsReconciliationJob() {
		return new JobBuilder(JOB_NAME, jobRepository)
			.incrementer(new RunIdIncrementer())
			.start(reconciliationStep())
			.listener(jobListener)
			.build();
	}


	@JobScope
	@Bean(STEP_NAME)
	public Step reconciliationStep() {
		return new StepBuilder(STEP_NAME, jobRepository)
			.tasklet(tasklet, transactionManager)
			.listener(stepMonitorListener)
			.build();
	}

}
