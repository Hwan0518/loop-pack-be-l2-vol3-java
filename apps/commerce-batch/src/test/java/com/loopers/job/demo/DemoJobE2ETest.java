package com.loopers.job.demo;

import com.loopers.batch.job.demo.DemoJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + DemoJobConfig.JOB_NAME)
@DisplayName("DemoJob E2E 테스트")
class DemoJobE2ETest {

    // IDE 정적 분석 상 [SpringBatchTest] 의 주입보다 [SpringBootTest] 의 주입이 우선되어, 해당 컴포넌트는 없으므로 오류처럼 보일 수 있음.
    // [SpringBatchTest] 자체가 Scope 기반으로 주입하기 때문에 정상 동작함.
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(DemoJobConfig.JOB_NAME)
    private Job job;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @DisplayName("[demoJob] requestDate 파라미터 미전달 -> FAILED. Step 1개 실행되며 해당 Step도 FAILED, 에러 메시지에 'requestDate is null' 포함")
    @Test
    void shouldFailJob_whenRequestDateIsMissing() throws Exception {
        // Act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // Assert — Job/Step 상태 및 실패 원인 검증
        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.FAILED.getExitCode()),
            () -> assertThat(jobExecution.getStepExecutions()).hasSize(1),
            () -> assertThat(stepExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.FAILED.getExitCode()),
            () -> assertThat(stepExecution.getExitStatus().getExitDescription())
                .contains("requestDate is null")
        );
    }

    @DisplayName("[demoJob] 유효한 requestDate 전달 -> COMPLETED. Step 1개 실행 성공, commit 1회")
    @Test
    void shouldCompleteJob_whenRequestDateIsProvided() throws Exception {
        // Arrange
        var jobParameters = new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.now())
            .toJobParameters();

        // Act
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Assert — Job/Step 상태 및 실행 결과 검증
        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(jobExecution.getStepExecutions()).hasSize(1),
            () -> assertThat(stepExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(stepExecution.getCommitCount()).isEqualTo(1)
        );
    }
}
