package com.loopers.cart.cart.infrastructure.mapper;


import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.cart.cart.infrastructure.entity.CartItemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("CartItemEntityMapper 테스트")
class CartItemEntityMapperTest {

	private CartItemEntityMapper mapper;


	@BeforeEach
	void setUp() {
		mapper = new CartItemEntityMapper();
	}


	@Nested
	@DisplayName("toEntity() - 도메인 -> 엔티티 변환")
	class ToEntityTest {

		@Test
		@DisplayName("[toEntity()] CartItem -> CartItemEntity 변환. userId, productId, quantity, selected 매핑 확인")
		void toEntity() {
			// Arrange
			CartItem cartItem = CartItem.create(10L, 100L, 3L);

			// Act
			CartItemEntity entity = mapper.toEntity(cartItem);

			// Assert
			assertAll(
				() -> assertThat(entity.getUserId()).isEqualTo(10L),
				() -> assertThat(entity.getProductId()).isEqualTo(100L),
				() -> assertThat(entity.getQuantity()).isEqualTo(3L),
				() -> assertThat(entity.isSelected()).isTrue()
			);
		}


		@Test
		@DisplayName("[toEntity()] 선택 해제 상태인 CartItem -> selected=false인 CartItemEntity 변환")
		void toEntityDeselected() {
			// Arrange
			CartItem cartItem = CartItem.reconstruct(
				null, 10L, 100L, Quantity.from(5L), false,
				LocalDateTime.now(), LocalDateTime.now()
			);

			// Act
			CartItemEntity entity = mapper.toEntity(cartItem);

			// Assert
			assertAll(
				() -> assertThat(entity.getQuantity()).isEqualTo(5L),
				() -> assertThat(entity.isSelected()).isFalse()
			);
		}

	}


	@Nested
	@DisplayName("toDomain() - 엔티티 -> 도메인 변환")
	class ToDomainTest {

		@Test
		@DisplayName("[toDomain()] CartItemEntity -> CartItem 변환. userId, productId, quantity, selected 매핑 확인")
		void toDomain() {
			// Arrange
			CartItem original = CartItem.create(10L, 100L, 3L);
			CartItemEntity entity = mapper.toEntity(original);

			// Act
			CartItem domain = mapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(domain.getUserId()).isEqualTo(10L),
				() -> assertThat(domain.getProductId()).isEqualTo(100L),
				() -> assertThat(domain.getQuantity().value()).isEqualTo(3L),
				() -> assertThat(domain.isSelected()).isTrue()
			);
		}

	}


	@Nested
	@DisplayName("양방향 변환 일관성 테스트")
	class RoundTripTest {

		@Test
		@DisplayName("[toEntity() → toDomain()] 도메인 → 엔티티 → 도메인 변환 시 비즈니스 필드 보존. "
			+ "userId, productId, quantity, selected 값이 원본과 동일")
		void roundTripPreservesFields() {
			// Arrange
			CartItem original = CartItem.create(10L, 100L, 3L);

			// Act
			CartItemEntity entity = mapper.toEntity(original);
			CartItem reconstructed = mapper.toDomain(entity);

			// Assert — 원본 도메인과 복원된 도메인의 비즈니스 필드 일치 검증
			assertAll(
				() -> assertThat(reconstructed.getUserId()).isEqualTo(original.getUserId()),
				() -> assertThat(reconstructed.getProductId()).isEqualTo(original.getProductId()),
				() -> assertThat(reconstructed.getQuantity().value()).isEqualTo(original.getQuantity().value()),
				() -> assertThat(reconstructed.isSelected()).isEqualTo(original.isSelected())
			);
		}

	}

}
