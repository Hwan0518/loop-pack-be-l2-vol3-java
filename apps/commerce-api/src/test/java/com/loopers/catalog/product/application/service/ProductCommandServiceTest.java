package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.domain.event.ProductDeletedEvent;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCommandService 단위 테스트")
class ProductCommandServiceTest {

	@Mock
	private ProductCommandRepository productCommandRepository;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ProductCommandService productCommandService;


	@BeforeEach
	void setUp() {
		productCommandService = new ProductCommandService(productCommandRepository, eventPublisher);
	}


	@Nested
	@DisplayName("createProduct()")
	class CreateProductTest {

		@Test
		@DisplayName("[createProduct()] 유효한 InDto -> Product 생성 및 저장")
		void createProductSuccess() {
			// Arrange
			AdminProductCreateInDto inDto = new AdminProductCreateInDto(
				1L, "테스트 상품", new BigDecimal("10000"), 100L, "설명"
			);
			given(productCommandRepository.save(any(Product.class))).willAnswer(invocation -> {
				Product p = invocation.getArgument(0);
				return Product.reconstruct(1L, p.getBrandId(), p.getName(), p.getPrice(),
					p.getStock(), p.getDescription(), p.getLikeCount(), p.getDeletedAt());
			});

			// Act
			Product result = productCommandService.createProduct(inDto);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getBrandId()).isEqualTo(1L),
				() -> assertThat(result.getName().value()).isEqualTo("테스트 상품"),
				() -> verify(productCommandRepository).save(any(Product.class))
			);
		}

	}


	@Nested
	@DisplayName("updateProduct()")
	class UpdateProductTest {

		@Test
		@DisplayName("[updateProduct()] 유효한 수정 요청 -> Product 수정 및 저장")
		void updateProductSuccess() {
			// Arrange
			Product product = Product.reconstruct(1L, 1L,
				ProductName.from("원래 상품"),
				Money.from(new BigDecimal("10000")),
				Stock.from(100L),
				null, 0L, null);
			AdminProductUpdateInDto inDto = new AdminProductUpdateInDto(
				"수정 상품", new BigDecimal("20000"), 200L, "수정 설명"
			);
			given(productCommandRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			Product result = productCommandService.updateProduct(product, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.getName().value()).isEqualTo("수정 상품"),
				() -> assertThat(result.getPrice().value()).isEqualByComparingTo(new BigDecimal("20000")),
				() -> assertThat(result.getStock().value()).isEqualTo(200L),
				() -> verify(productCommandRepository).save(any(Product.class))
			);
		}

	}


	@Nested
	@DisplayName("deleteProduct()")
	class DeleteProductTest {

		@Test
		@DisplayName("[deleteProduct()] 활성 상품 -> soft delete + 이벤트 발행")
		void deleteProductSuccess() {
			// Arrange
			Product product = Product.reconstruct(1L, 1L,
				ProductName.from("상품"),
				Money.from(BigDecimal.TEN),
				Stock.from(100L),
				null, 0L, null);

			// Act
			productCommandService.deleteProduct(product);

			// Assert
			ArgumentCaptor<ProductDeletedEvent> eventCaptor = ArgumentCaptor.forClass(ProductDeletedEvent.class);
			assertAll(
				() -> assertThat(product.isDeleted()).isTrue(),
				() -> verify(productCommandRepository).delete(product),
				() -> {
					verify(eventPublisher).publishEvent(eventCaptor.capture());
					assertThat(eventCaptor.getValue().productId()).isEqualTo(1L);
				}
			);
		}

	}


	@Nested
	@DisplayName("increaseLikeCount()")
	class IncreaseLikeCountTest {

		@Test
		@DisplayName("[increaseLikeCount()] 상품 좋아요 증가 -> likeCount + 1 저장")
		void increaseLikeCountSuccess() {
			// Arrange
			Product product = Product.reconstruct(1L, 1L,
				ProductName.from("상품"),
				Money.from(BigDecimal.TEN),
				Stock.from(100L),
				null, 5L, null);
			given(productCommandRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			productCommandService.increaseLikeCount(product);

			// Assert
			assertAll(
				() -> assertThat(product.getLikeCount()).isEqualTo(6L),
				() -> verify(productCommandRepository).save(product)
			);
		}

	}


	@Nested
	@DisplayName("decreaseLikeCount()")
	class DecreaseLikeCountTest {

		@Test
		@DisplayName("[decreaseLikeCount()] 상품 좋아요 감소 -> likeCount - 1 저장")
		void decreaseLikeCountSuccess() {
			// Arrange
			Product product = Product.reconstruct(1L, 1L,
				ProductName.from("상품"),
				Money.from(BigDecimal.TEN),
				Stock.from(100L),
				null, 5L, null);
			given(productCommandRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			productCommandService.decreaseLikeCount(product);

			// Assert
			assertAll(
				() -> assertThat(product.getLikeCount()).isEqualTo(4L),
				() -> verify(productCommandRepository).save(product)
			);
		}

	}

}
