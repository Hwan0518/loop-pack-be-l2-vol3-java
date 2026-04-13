package com.loopers.batch.job.ranking;


import com.loopers.batch.job.ranking.step.RankingRebuildTasklet;
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
 * 랭킹 재계산 Job
 * - Job Parameters: from (yyyyMMdd), to (yyyyMMdd), scorerType (기본 SATURATION), carryOverWeight (기본 0.1)
 * - 날짜 순차로 daily_counter → scorer → daily_score + Redis ZSET 재생성
 * - carry-over chain 보존
 */

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingRebuildJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingRebuildJobConfig {

	public static final String JOB_NAME = "rankingRebuildJob";
	private static final String STEP_NAME = "rankingRebuildStep";

	private final JobRepository jobRepository;
	private final JobListener jobListener;
	private final StepMonitorListener stepMonitorListener;
	private final RankingRebuildTasklet tasklet;
	private final PlatformTransactionManager transactionManager;


	@Bean(JOB_NAME)
	public Job rankingRebuildJob() {
		return new JobBuilder(JOB_NAME, jobRepository)
			.incrementer(new RunIdIncrementer())
			.start(rankingRebuildStep())
			.listener(jobListener)
			.build();
	}


	@JobScope
	@Bean(STEP_NAME)
	public Step rankingRebuildStep() {
		return new StepBuilder(STEP_NAME, jobRepository)
			.tasklet(tasklet, transactionManager)
			.listener(stepMonitorListener)
			.build();
	}

}
