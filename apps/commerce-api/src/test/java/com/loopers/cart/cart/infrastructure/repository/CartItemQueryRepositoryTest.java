package com.loopers.cart.cart.infrastructure.repository;


import com.loopers.cart.cart.domain.model.CartItem;
import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.cart.cart.infrastructure.entity.CartItemEntity;
import com.loopers.cart.cart.infrastructure.jpa.CartItemJpaRepository;
import com.loopers.cart.cart.infrastructure.mapper.CartItemEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("CartItemQueryRepositoryImpl 단위 테스트")
class CartItemQueryRepositoryTest {

	@Mock
	private CartItemJpaRepository cartItemJpaRepository;

	@Mock
	private CartItemEntityMapper cartItemMapper;

	private CartItemQueryRepositoryImpl cartItemQueryRepository;


	@BeforeEach
	void setUp() {
		cartItemQueryRepository = new CartItemQueryRepositoryImpl(cartItemJpaRepository, cartItemMapper);
	}


	@Test
	@DisplayName("[findById()] 존재하는 ID -> CartItem 반환")
	void findByIdSuccess() {
		// Arrange
		CartItemEntity entity = CartItemEntity.of(1L, 100L, 3L, true);
		CartItem domain = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(3L), true,
			LocalDateTime.now(), LocalDateTime.now());
		given(cartItemJpaRepository.findById(1L)).willReturn(Optional.of(entity));
		given(cartItemMapper.toDomain(entity)).willReturn(domain);

		// Act
		Optional<CartItem> result = cartItemQueryRepository.findById(1L);

		// Assert
		assertAll(
			() -> assertThat(result).isPresent(),
			() -> assertThat(result.get().getUserId()).isEqualTo(1L),
			() -> assertThat(result.get().getProductId()).isEqualTo(100L)
		);
	}


	@Test
	@DisplayName("[findById()] 존재하지 않는 ID -> 빈 Optional 반환")
	void findByIdNotFound() {
		// Arrange
		given(cartItemJpaRepository.findById(999L)).willReturn(Optional.empty());

		// Act
		Optional<CartItem> result = cartItemQueryRepository.findById(999L);

		// Assert
		assertThat(result).isEmpty();
	}


	@Test
	@DisplayName("[findByUserId()] 사용자의 장바구니 항목 목록 조회 -> 해당 사용자의 항목만 반환")
	void findByUserId() {
		// Arrange
		CartItemEntity entity1 = CartItemEntity.of(1L, 100L, 1L, true);
		CartItemEntity entity2 = CartItemEntity.of(1L, 200L, 2L, true);
		CartItem domain1 = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(1L), true,
			LocalDateTime.now(), LocalDateTime.now());
		CartItem domain2 = CartItem.reconstruct(2L, 1L, 200L, Quantity.from(2L), true,
			LocalDateTime.now(), LocalDateTime.now());

		given(cartItemJpaRepository.findByUserId(1L)).willReturn(List.of(entity1, entity2));
		given(cartItemMapper.toDomain(entity1)).willReturn(domain1);
		given(cartItemMapper.toDomain(entity2)).willReturn(domain2);

		// Act
		List<CartItem> result = cartItemQueryRepository.findByUserId(1L);

		// Assert
		assertThat(result).hasSize(2);
	}


	@Test
	@DisplayName("[findByUserId()] 장바구니 항목이 없는 사용자 -> 빈 목록 반환")
	void findByUserIdEmpty() {
		// Arrange
		given(cartItemJpaRepository.findByUserId(999L)).willReturn(List.of());

		// Act
		List<CartItem> result = cartItemQueryRepository.findByUserId(999L);

		// Assert
		assertThat(result).isEmpty();
	}


	@Test
	@DisplayName("[findByUserIdAndProductId()] 사용자 ID + 상품 ID로 조회 -> 해당 항목 반환")
	void findByUserIdAndProductId() {
		// Arrange
		CartItemEntity entity = CartItemEntity.of(1L, 100L, 3L, true);
		CartItem domain = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(3L), true,
			LocalDateTime.now(), LocalDateTime.now());
		given(cartItemJpaRepository.findByUserIdAndProductId(1L, 100L)).willReturn(Optional.of(entity));
		given(cartItemMapper.toDomain(entity)).willReturn(domain);

		// Act
		Optional<CartItem> result = cartItemQueryRepository.findByUserIdAndProductId(1L, 100L);

		// Assert
		assertAll(
			() -> assertThat(result).isPresent(),
			() -> assertThat(result.get().getUserId()).isEqualTo(1L),
			() -> assertThat(result.get().getProductId()).isEqualTo(100L)
		);
	}


	@Test
	@DisplayName("[findByUserIdAndProductId()] 존재하지 않는 조합 -> 빈 Optional 반환")
	void findByUserIdAndProductIdNotFound() {
		// Arrange
		given(cartItemJpaRepository.findByUserIdAndProductId(1L, 999L)).willReturn(Optional.empty());

		// Act
		Optional<CartItem> result = cartItemQueryRepository.findByUserIdAndProductId(1L, 999L);

		// Assert
		assertThat(result).isEmpty();
	}


	@Test
	@DisplayName("[findSelectedByUserId()] 선택된 항목만 조회 -> 선택된 항목만 반환")
	void findSelectedByUserId() {
		// Arrange
		CartItemEntity entity = CartItemEntity.of(1L, 100L, 1L, true);
		CartItem domain = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(1L), true,
			LocalDateTime.now(), LocalDateTime.now());
		given(cartItemJpaRepository.findByUserIdAndSelectedTrue(1L)).willReturn(List.of(entity));
		given(cartItemMapper.toDomain(entity)).willReturn(domain);

		// Act
		List<CartItem> result = cartItemQueryRepository.findSelectedByUserId(1L);

		// Assert
		assertAll(
			() -> assertThat(result).hasSize(1),
			() -> assertThat(result.get(0).isSelected()).isTrue()
		);
	}


	@Test
	@DisplayName("[findByUserIdAndIds()] 사용자 ID + ID 목록으로 조회 -> 해당 항목만 반환")
	void findByUserIdAndIds() {
		// Arrange
		CartItemEntity entity1 = CartItemEntity.of(1L, 100L, 1L, true);
		CartItemEntity entity2 = CartItemEntity.of(1L, 200L, 2L, true);
		CartItem domain1 = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(1L), true,
			LocalDateTime.now(), LocalDateTime.now());
		CartItem domain2 = CartItem.reconstruct(2L, 1L, 200L, Quantity.from(2L), true,
			LocalDateTime.now(), LocalDateTime.now());

		List<Long> ids = List.of(1L, 2L);
		given(cartItemJpaRepository.findByUserIdAndIdIn(1L, ids)).willReturn(List.of(entity1, entity2));
		given(cartItemMapper.toDomain(entity1)).willReturn(domain1);
		given(cartItemMapper.toDomain(entity2)).willReturn(domain2);

		// Act
		List<CartItem> result = cartItemQueryRepository.findByUserIdAndIds(1L, ids);

		// Assert
		assertThat(result).hasSize(2);
	}


	@Test
	@DisplayName("[findByUserIdAndIds()] 일치하는 항목 없음 -> 빈 목록 반환")
	void findByUserIdAndIdsEmpty() {
		// Arrange
		List<Long> ids = List.of(999L);
		given(cartItemJpaRepository.findByUserIdAndIdIn(1L, ids)).willReturn(List.of());

		// Act
		List<CartItem> result = cartItemQueryRepository.findByUserIdAndIds(1L, ids);

		// Assert
		assertThat(result).isEmpty();
	}

}
