package com.loopers.coupon.issuedcoupon.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.loopers.coupon.issuedcoupon.domain.model.CouponIssueRequest;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponIssueRequestStatus;
import com.loopers.coupon.issuedcoupon.domain.repository.CouponIssueRequestCommandRepository;
import com.loopers.coupon.issuedcoupon.domain.repository.CouponIssueRequestQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.event.EventType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssueRequestCommandService 단위 테스트")
class CouponIssueRequestCommandServiceTest {

	@Mock
	private CouponIssueRequestCommandRepository couponIssueRequestCommandRepository;

	@Mock
	private CouponIssueRequestQueryRepository couponIssueRequestQueryRepository;

	@Mock
	private OutboxEventPort outboxEventPort;

	@Mock
	private JsonSerializer jsonSerializer;

	private CouponIssueRequestCommandService service;


	@BeforeEach
	void setUp() {
		service = new CouponIssueRequestCommandService(
			couponIssueRequestCommandRepository,
			couponIssueRequestQueryRepository,
			outboxEventPort,
			jsonSerializer
		);
	}


	private CouponIssueRequest existingRequest(
		String requestId,
		Long userId,
		Long couponTemplateId,
		CouponIssueRequestStatus status
	) {
		return CouponIssueRequest.reconstruct(
			1L,
			requestId,
			userId,
			couponTemplateId,
			status,
			null,
			LocalDateTime.now()
		);
	}


	@Nested
	@DisplayName("createIssueRequest()")
	class CreateIssueRequestTest {

		@Test
		@DisplayName("[createIssueRequest()] requestId 미전달 + 기존 요청 없음 -> UUID 생성 후 PENDING 저장, Outbox 저장")
		void createIssueRequest_withoutRequestId_savesPendingAndOutbox() {
			// Arrange
			Long userId = 100L;
			Long couponTemplateId = 11L;
			given(couponIssueRequestQueryRepository.findByRequestId(anyString())).willReturn(Optional.empty());
			given(couponIssueRequestCommandRepository.save(any(CouponIssueRequest.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
			given(jsonSerializer.toJson(any())).willReturn("{\"event\":\"coupon\"}");

			ArgumentCaptor<CouponIssueRequest> requestCaptor = ArgumentCaptor.forClass(CouponIssueRequest.class);
			ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);

			// Act
			var result = service.createIssueRequest(userId, couponTemplateId, null);

			// Assert
			verify(couponIssueRequestCommandRepository).save(requestCaptor.capture());
			verify(outboxEventPort).save(
				org.mockito.ArgumentMatchers.eq(EventType.COUPON_ISSUE_REQUESTED),
				org.mockito.ArgumentMatchers.eq(String.valueOf(couponTemplateId)),
				org.mockito.ArgumentMatchers.eq(String.valueOf(couponTemplateId)),
				org.mockito.ArgumentMatchers.eq("{\"event\":\"coupon\"}")
			);
			verify(couponIssueRequestQueryRepository).findByRequestId(requestIdCaptor.capture());

			CouponIssueRequest savedRequest = requestCaptor.getValue();
			String generatedRequestId = requestIdCaptor.getValue();
			assertAll(
				() -> assertThat(generatedRequestId).isNotBlank(),
				() -> assertThat(savedRequest.getRequestId()).isEqualTo(generatedRequestId),
				() -> assertThat(savedRequest.getUserId()).isEqualTo(userId),
				() -> assertThat(savedRequest.getCouponTemplateId()).isEqualTo(couponTemplateId),
				() -> assertThat(savedRequest.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING),
				() -> assertThat(result.requestId()).isEqualTo(generatedRequestId),
				() -> assertThat(result.status()).isEqualTo("PENDING")
			);
		}


		@Test
		@DisplayName("[createIssueRequest()] blank requestId + 기존 요청 없음 -> UUID 생성 후 PENDING 저장")
		void createIssueRequest_blankRequestId_generatesUuid() {
			// Arrange
			Long userId = 101L;
			Long couponTemplateId = 12L;
			given(couponIssueRequestQueryRepository.findByRequestId(anyString())).willReturn(Optional.empty());
			given(couponIssueRequestCommandRepository.save(any(CouponIssueRequest.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
			given(jsonSerializer.toJson(any())).willReturn("{\"event\":\"coupon\"}");

			ArgumentCaptor<CouponIssueRequest> requestCaptor = ArgumentCaptor.forClass(CouponIssueRequest.class);

			// Act
			var result = service.createIssueRequest(userId, couponTemplateId, "   ");

			// Assert
			verify(couponIssueRequestCommandRepository).save(requestCaptor.capture());
			assertAll(
				() -> assertThat(requestCaptor.getValue().getRequestId()).isNotBlank(),
				() -> assertThat(requestCaptor.getValue().getUserId()).isEqualTo(userId),
				() -> assertThat(requestCaptor.getValue().getCouponTemplateId()).isEqualTo(couponTemplateId),
				() -> assertThat(result.requestId()).isEqualTo(requestCaptor.getValue().getRequestId()),
				() -> assertThat(result.status()).isEqualTo("PENDING")
			);
		}


		@Test
		@DisplayName("[createIssueRequest()] 동일 userId의 기존 requestId 존재 -> 기존 상태 반환, 저장/Outbox 미호출")
		void createIssueRequest_existingRequestBySameUser_returnsExisting() {
			// Arrange
			String requestId = "coupon-request-1";
			CouponIssueRequest existing = existingRequest(
				requestId,
				100L,
				11L,
				CouponIssueRequestStatus.PENDING
			);
			given(couponIssueRequestQueryRepository.findByRequestId(requestId))
				.willReturn(Optional.of(existing));

			// Act
			var result = service.createIssueRequest(100L, 11L, requestId);

			// Assert
			assertAll(
				() -> assertThat(result.requestId()).isEqualTo(requestId),
				() -> assertThat(result.status()).isEqualTo("PENDING")
			);
			verify(couponIssueRequestCommandRepository, never()).save(any());
			verify(outboxEventPort, never()).save(any(), anyString(), anyString(), anyString());
			verify(jsonSerializer, never()).toJson(any());
		}


		@Test
		@DisplayName("[createIssueRequest()] 다른 userId의 기존 requestId 존재 -> COUPON_ISSUE_REQUEST_NOT_FOUND 예외")
		void createIssueRequest_existingRequestByAnotherUser_throwsNotFound() {
			// Arrange
			String requestId = "coupon-request-2";
			CouponIssueRequest existing = existingRequest(
				requestId,
				200L,
				11L,
				CouponIssueRequestStatus.PENDING
			);
			given(couponIssueRequestQueryRepository.findByRequestId(requestId))
				.willReturn(Optional.of(existing));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.createIssueRequest(100L, 11L, requestId));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND.getMessage())
			);
			verify(couponIssueRequestCommandRepository, never()).save(any());
			verify(outboxEventPort, never()).save(any(), anyString(), anyString(), anyString());
		}
	}


	@Nested
	@DisplayName("getIssueRequest()")
	class GetIssueRequestTest {

		@Test
		@DisplayName("[getIssueRequest()] requestId + userId 일치하는 요청 존재 -> 상태 조회 성공")
		void getIssueRequest_existingRequest_returnsDto() {
			// Arrange
			String requestId = "coupon-request-3";
			CouponIssueRequest request = existingRequest(
				requestId,
				100L,
				11L,
				CouponIssueRequestStatus.ISSUED
			);
			given(couponIssueRequestQueryRepository.findByRequestIdAndUserId(requestId, 100L))
				.willReturn(Optional.of(request));

			// Act
			var result = service.getIssueRequest(requestId, 100L);

			// Assert
			assertAll(
				() -> assertThat(result.requestId()).isEqualTo(requestId),
				() -> assertThat(result.status()).isEqualTo("ISSUED")
			);
		}


		@Test
		@DisplayName("[getIssueRequest()] 요청이 없거나 다른 사용자 요청 -> COUPON_ISSUE_REQUEST_NOT_FOUND 예외")
		void getIssueRequest_missingRequest_throwsNotFound() {
			// Arrange
			given(couponIssueRequestQueryRepository.findByRequestIdAndUserId("missing-request", 100L))
				.willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.getIssueRequest("missing-request", 100L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND.getMessage())
			);
		}
	}
}
