package com.loopers.engagement.productlike.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("ProductLike 도메인 모델 테스트")
class ProductLikeTest {

	@Nested
	@DisplayName("create() - 상품 좋아요 생성")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 파라미터 -> 성공. id=null, createdAt=null")
		void createSuccess() {
			// Act
			ProductLike like = ProductLike.create(1L, 100L);

			// Assert
			assertAll(
				() -> assertThat(like.getId()).isNull(),
				() -> assertThat(like.getUserId()).isEqualTo(1L),
				() -> assertThat(like.getTargetId()).isEqualTo(100L),
				() -> assertThat(like.getCreatedAt()).isNull()
			);
		}

		@Test
		@DisplayName("[create()] userId가 null -> NullPointerException")
		void createNullUserId() {
			assertThrows(NullPointerException.class,
				() -> ProductLike.create(null, 100L));
		}

		@Test
		@DisplayName("[create()] targetId가 null -> NullPointerException")
		void createNullTargetId() {
			assertThrows(NullPointerException.class,
				() -> ProductLike.create(1L, null));
		}

		@Test
		@DisplayName("[create()] targetId가 0 -> INVALID_LIKE_TARGET 예외")
		void createZeroTargetId() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> ProductLike.create(1L, 0L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LIKE_TARGET),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LIKE_TARGET.getMessage())
			);
		}

		@Test
		@DisplayName("[create()] targetId가 음수 -> INVALID_LIKE_TARGET 예외")
		void createNegativeTargetId() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> ProductLike.create(1L, -1L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LIKE_TARGET);
		}
	}


	@Nested
	@DisplayName("reconstruct() - 상품 좋아요 재생성")
	class ReconstructTest {

		@Test
		@DisplayName("[reconstruct()] 전체 필드 -> 성공. 모든 필드 설정됨")
		void reconstructSuccess() {
			// Arrange
			LocalDateTime now = LocalDateTime.now();

			// Act
			ProductLike like = ProductLike.reconstruct(1L, 2L, 100L, now);

			// Assert
			assertAll(
				() -> assertThat(like.getId()).isEqualTo(1L),
				() -> assertThat(like.getUserId()).isEqualTo(2L),
				() -> assertThat(like.getTargetId()).isEqualTo(100L),
				() -> assertThat(like.getCreatedAt()).isEqualTo(now)
			);
		}
	}

}
