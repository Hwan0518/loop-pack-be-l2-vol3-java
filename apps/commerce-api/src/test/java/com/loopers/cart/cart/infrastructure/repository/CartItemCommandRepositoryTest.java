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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("CartItemCommandRepositoryImpl 단위 테스트")
class CartItemCommandRepositoryTest {

	@Mock
	private CartItemJpaRepository cartItemJpaRepository;

	@Mock
	private CartItemEntityMapper cartItemMapper;

	private CartItemCommandRepositoryImpl cartItemCommandRepository;


	@BeforeEach
	void setUp() {
		cartItemCommandRepository = new CartItemCommandRepositoryImpl(cartItemJpaRepository, cartItemMapper);
	}


	@Test
	@DisplayName("[save()] 신규 CartItem 저장 -> toEntity 변환 후 JPA 저장, toDomain 변환 후 반환")
	void saveNewCartItem() {
		// Arrange
		CartItem newCartItem = CartItem.create(1L, 100L, 3L);
		CartItemEntity entity = CartItemEntity.of(1L, 100L, 3L, true);
		CartItem savedDomain = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(3L), true,
			LocalDateTime.now(), LocalDateTime.now());

		given(cartItemMapper.toEntity(newCartItem)).willReturn(entity);
		given(cartItemJpaRepository.save(entity)).willReturn(entity);
		given(cartItemMapper.toDomain(entity)).willReturn(savedDomain);

		// Act
		CartItem result = cartItemCommandRepository.save(newCartItem);

		// Assert
		assertAll(
			() -> assertThat(result.getId()).isEqualTo(1L),
			() -> assertThat(result.getUserId()).isEqualTo(1L),
			() -> assertThat(result.getProductId()).isEqualTo(100L),
			() -> assertThat(result.getQuantity().value()).isEqualTo(3L)
		);
	}


	@Test
	@DisplayName("[save()] 기존 CartItem 업데이트 -> toEntity 변환 후 JPA 저장, toDomain 변환 후 반환")
	void saveExistingCartItem() {
		// Arrange
		CartItem existingCartItem = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(10L), true,
			LocalDateTime.now(), LocalDateTime.now());
		CartItemEntity entity = CartItemEntity.of(1L, 100L, 10L, true);
		CartItem updatedDomain = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(10L), true,
			LocalDateTime.now(), LocalDateTime.now());

		given(cartItemMapper.toEntity(existingCartItem)).willReturn(entity);
		given(cartItemJpaRepository.save(entity)).willReturn(entity);
		given(cartItemMapper.toDomain(entity)).willReturn(updatedDomain);

		// Act
		CartItem result = cartItemCommandRepository.save(existingCartItem);

		// Assert
		assertAll(
			() -> assertThat(result.getId()).isEqualTo(1L),
			() -> assertThat(result.getQuantity().value()).isEqualTo(10L)
		);
		verify(cartItemMapper).toEntity(existingCartItem);
	}


	@Test
	@DisplayName("[delete()] CartItem 삭제 -> deleteById 호출")
	void deleteCartItem() {
		// Arrange
		CartItem cartItem = CartItem.reconstruct(1L, 1L, 100L, Quantity.from(3L), true,
			LocalDateTime.now(), LocalDateTime.now());
		willDoNothing().given(cartItemJpaRepository).deleteById(1L);

		// Act
		cartItemCommandRepository.delete(cartItem);

		// Assert
		verify(cartItemJpaRepository).deleteById(1L);
	}


	@Test
	@DisplayName("[deleteAllByProductId()] 상품 ID로 전체 삭제 -> JPA deleteAllByProductId 호출")
	void deleteAllByProductId() {
		// Arrange
		willDoNothing().given(cartItemJpaRepository).deleteAllByProductId(100L);

		// Act
		cartItemCommandRepository.deleteAllByProductId(100L);

		// Assert
		verify(cartItemJpaRepository).deleteAllByProductId(100L);
	}


	@Test
	@DisplayName("[deleteAllByIds()] ID 목록으로 전체 삭제 -> JPA deleteAllByIdIn 호출")
	void deleteAllByIds() {
		// Arrange
		List<Long> ids = List.of(1L, 2L, 3L);
		willDoNothing().given(cartItemJpaRepository).deleteAllByIdIn(ids);

		// Act
		cartItemCommandRepository.deleteAllByIds(ids);

		// Assert
		verify(cartItemJpaRepository).deleteAllByIdIn(ids);
	}


	@Test
	@DisplayName("[deleteAllByIds()] 빈 목록 -> JPA 호출하지 않음")
	void deleteAllByIdsEmpty() {
		// Act
		cartItemCommandRepository.deleteAllByIds(List.of());

		// Assert
		verify(cartItemJpaRepository, org.mockito.Mockito.never()).deleteAllByIdIn(any());
	}


	@Test
	@DisplayName("[deleteAllByUserIdAndIds()] 사용자 ID와 ID 목록으로 전체 삭제 -> 해당 사용자의 항목만 JPA deleteAllByUserIdAndIdIn 호출")
	void deleteAllByUserIdAndIds() {
		// Arrange
		Long userId = 1L;
		List<Long> ids = List.of(1L, 2L, 3L);
		willDoNothing().given(cartItemJpaRepository).deleteAllByUserIdAndIdIn(userId, ids);

		// Act
		cartItemCommandRepository.deleteAllByUserIdAndIds(userId, ids);

		// Assert
		verify(cartItemJpaRepository).deleteAllByUserIdAndIdIn(userId, ids);
	}


	@Test
	@DisplayName("[deleteAllByUserIdAndIds()] 빈 목록 -> JPA 호출하지 않음")
	void deleteAllByUserIdAndIdsEmpty() {
		// Act
		cartItemCommandRepository.deleteAllByUserIdAndIds(1L, List.of());

		// Assert
		verify(cartItemJpaRepository, org.mockito.Mockito.never()).deleteAllByUserIdAndIdIn(anyLong(), any());
	}


	@Test
	@DisplayName("[deleteAllByUserIdAndIds()] null 목록 -> JPA 호출하지 않음")
	void deleteAllByUserIdAndIdsNull() {
		// Act
		cartItemCommandRepository.deleteAllByUserIdAndIds(1L, null);

		// Assert
		verify(cartItemJpaRepository, org.mockito.Mockito.never()).deleteAllByUserIdAndIdIn(anyLong(), any());
	}

}
