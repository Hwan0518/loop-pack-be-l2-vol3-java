package com.loopers.coupon.issuedcoupon.application.service;


import com.loopers.coupon.issuedcoupon.application.port.out.client.user.UserAuthenticator;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("IssuedCouponQueryService 단위 테스트")
class IssuedCouponQueryServiceTest {

	@Mock
	private IssuedCouponQueryRepository issuedCouponQueryRepository;
	@Mock
	private UserAuthenticator userAuthenticator;

	private IssuedCouponQueryService issuedCouponQueryService;


	@BeforeEach
	void setUp() {
		issuedCouponQueryService = new IssuedCouponQueryService(
			issuedCouponQueryRepository, userAuthenticator
		);
	}


	@Nested
	@DisplayName("authenticate()")
	class AuthenticateTest {

		@Test
		@DisplayName("[authenticate()] 유효한 인증 정보 -> userId 반환. UserAuthenticator에 위임하여 사용자 ID를 반환한다")
		void authenticateSuccess() {
			// Arrange
			given(userAuthenticator.authenticate("user1", "pw1")).willReturn(100L);

			// Act
			Long userId = issuedCouponQueryService.authenticate("user1", "pw1");

			// Assert
			assertThat(userId).isEqualTo(100L);
			verify(userAuthenticator).authenticate("user1", "pw1");
		}

	}


	@Nested
	@DisplayName("getById()")
	class GetByIdTest {

		@Test
		@DisplayName("[getById()] 존재하는 ID -> IssuedCoupon 반환. 발급 쿠폰이 존재하면 해당 도메인 객체를 반환한다")
		void getByIdFound() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(
				1L, 10L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now()
			);
			given(issuedCouponQueryRepository.findById(1L)).willReturn(Optional.of(issuedCoupon));

			// Act
			IssuedCoupon result = issuedCouponQueryService.getById(1L);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getCouponTemplateId()).isEqualTo(10L),
				() -> assertThat(result.getUserId()).isEqualTo(100L),
				() -> assertThat(result.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE)
			);
		}


		@Test
		@DisplayName("[getById()] 존재하지 않는 ID -> ISSUED_COUPON_NOT_FOUND 예외. 발급 쿠폰이 없으면 예외를 발생시킨다")
		void getByIdNotFound() {
			// Arrange
			given(issuedCouponQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponQueryService.getById(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.ISSUED_COUPON_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.ISSUED_COUPON_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("getAllByUserId()")
	class GetAllByUserIdTest {

		@Test
		@DisplayName("[getAllByUserId()] 사용자 ID -> 발급 쿠폰 목록 반환. 해당 사용자의 모든 발급 쿠폰을 반환한다")
		void getAllByUserIdSuccess() {
			// Arrange
			List<IssuedCoupon> issuedCoupons = List.of(
				IssuedCoupon.reconstruct(1L, 10L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now()),
				IssuedCoupon.reconstruct(2L, 20L, 100L, IssuedCouponStatus.USED, ZonedDateTime.now())
			);
			given(issuedCouponQueryRepository.findAllByUserId(100L)).willReturn(issuedCoupons);

			// Act
			List<IssuedCoupon> result = issuedCouponQueryService.getAllByUserId(100L);

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(2),
				() -> assertThat(result.get(0).getId()).isEqualTo(1L),
				() -> assertThat(result.get(1).getId()).isEqualTo(2L)
			);
			verify(issuedCouponQueryRepository).findAllByUserId(100L);
		}

	}


	@Nested
	@DisplayName("getAllByCouponTemplateId()")
	class GetAllByCouponTemplateIdTest {

		@Test
		@DisplayName("[getAllByCouponTemplateId()] 쿠폰 템플릿 ID -> 발급 쿠폰 목록 반환. 해당 템플릿의 모든 발급 쿠폰을 반환한다")
		void getAllByCouponTemplateIdSuccess() {
			// Arrange
			List<IssuedCoupon> issuedCoupons = List.of(
				IssuedCoupon.reconstruct(1L, 10L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now()),
				IssuedCoupon.reconstruct(2L, 10L, 200L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now())
			);
			given(issuedCouponQueryRepository.findAllByCouponTemplateId(10L)).willReturn(issuedCoupons);

			// Act
			List<IssuedCoupon> result = issuedCouponQueryService.getAllByCouponTemplateId(10L);

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(2),
				() -> assertThat(result.get(0).getUserId()).isEqualTo(100L),
				() -> assertThat(result.get(1).getUserId()).isEqualTo(200L)
			);
			verify(issuedCouponQueryRepository).findAllByCouponTemplateId(10L);
		}

	}

}
