package com.loopers.batch.job.ranking;


import com.loopers.batch.job.ranking.step.RankingReconcileTasklet;
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
 * 랭킹 Redis projection 복구 Job
 * - ranking_projection_dirty 에서 미처리 dirty date 를 소비하여 Redis ZSET 재생성
 * - cron (예: 매 5분) 또는 수동 실행
 */

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingReconcileJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingReconcileJobConfig {

	public static final String JOB_NAME = "rankingReconcileJob";
	private static final String STEP_NAME = "rankingReconcileStep";

	private final JobRepository jobRepository;
	private final JobListener jobListener;
	private final StepMonitorListener stepMonitorListener;
	private final RankingReconcileTasklet tasklet;
	private final PlatformTransactionManager transactionManager;


	@Bean(JOB_NAME)
	public Job rankingReconcileJob() {
		return new JobBuilder(JOB_NAME, jobRepository)
			.incrementer(new RunIdIncrementer())
			.start(rankingReconcileStep())
			.listener(jobListener)
			.build();
	}


	@JobScope
	@Bean(STEP_NAME)
	public Step rankingReconcileStep() {
		return new StepBuilder(STEP_NAME, jobRepository)
			.tasklet(tasklet, transactionManager)
			.listener(stepMonitorListener)
			.build();
	}

}
