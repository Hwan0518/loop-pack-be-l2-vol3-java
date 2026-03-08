package com.loopers.catalog.product.domain.model;


import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductDescription;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("Product 도메인 모델 테스트")
class ProductTest {

	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 입력 -> Product 생성 성공. id=null, likeCount=0, deletedAt=null")
		void createSuccess() {
			// Act
			Product product = Product.create(1L, "테스트 상품", new BigDecimal("10000"), 100L, "설명");

			// Assert
			assertAll(
				() -> assertThat(product.getId()).isNull(),
				() -> assertThat(product.getBrandId()).isEqualTo(1L),
				() -> assertThat(product.getName().value()).isEqualTo("테스트 상품"),
				() -> assertThat(product.getPrice().value()).isEqualByComparingTo(new BigDecimal("10000")),
				() -> assertThat(product.getStock().value()).isEqualTo(100L),
				() -> assertThat(product.getDescription().value()).isEqualTo("설명"),
				() -> assertThat(product.getLikeCount()).isEqualTo(0L),
				() -> assertThat(product.getDeletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[create()] description null -> Product 생성 성공. description=null")
		void createWithNullDescription() {
			// Act
			Product product = Product.create(1L, "테스트 상품", new BigDecimal("10000"), 100L, null);

			// Assert
			assertThat(product.getDescription()).isNull();
		}


		@Test
		@DisplayName("[create()] brandId null -> NullPointerException")
		void createWithNullBrandId() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Product.create(null, "테스트 상품", new BigDecimal("10000"), 100L, "설명"));
		}


		@Test
		@DisplayName("[create()] name null -> INVALID_PRODUCT_NAME 예외")
		void createWithNullName() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Product.create(1L, null, new BigDecimal("10000"), 100L, "설명"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_NAME);
		}


		@Test
		@DisplayName("[create()] price null -> INVALID_PRODUCT_PRICE 예외")
		void createWithNullPrice() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Product.create(1L, "테스트 상품", null, 100L, "설명"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_PRICE);
		}


		@Test
		@DisplayName("[create()] stock null -> INVALID_PRODUCT_STOCK 예외")
		void createWithNullStock() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Product.create(1L, "테스트 상품", new BigDecimal("10000"), null, "설명"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}

	}


	@Nested
	@DisplayName("reconstruct()")
	class ReconstructTest {

		@Test
		@DisplayName("[reconstruct()] 모든 필드 포함 -> Product 재생성 성공")
		void reconstructSuccess() {
			// Arrange
			ZonedDateTime deletedAt = ZonedDateTime.now();

			// Act
			Product product = Product.reconstruct(
				1L, 2L,
				ProductName.from("상품명"),
				Money.from(new BigDecimal("5000")),
				Stock.from(50L),
				ProductDescription.from("설명"),
				10L,
				deletedAt
			);

			// Assert
			assertAll(
				() -> assertThat(product.getId()).isEqualTo(1L),
				() -> assertThat(product.getBrandId()).isEqualTo(2L),
				() -> assertThat(product.getName().value()).isEqualTo("상품명"),
				() -> assertThat(product.getPrice().value()).isEqualByComparingTo(new BigDecimal("5000")),
				() -> assertThat(product.getStock().value()).isEqualTo(50L),
				() -> assertThat(product.getDescription().value()).isEqualTo("설명"),
				() -> assertThat(product.getLikeCount()).isEqualTo(10L),
				() -> assertThat(product.getDeletedAt()).isEqualTo(deletedAt)
			);
		}

	}


	@Nested
	@DisplayName("changeName()")
	class ChangeNameTest {

		@Test
		@DisplayName("[changeName()] 유효한 이름 -> 이름 변경 성공")
		void changeNameSuccess() {
			// Arrange
			Product product = Product.create(1L, "원래 이름", new BigDecimal("10000"), 100L, null);

			// Act
			product.changeName("변경 이름");

			// Assert
			assertThat(product.getName().value()).isEqualTo("변경 이름");
		}

	}


	@Nested
	@DisplayName("changePrice()")
	class ChangePriceTest {

		@Test
		@DisplayName("[changePrice()] 유효한 가격 -> 가격 변경 성공")
		void changePriceSuccess() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);

			// Act
			product.changePrice(new BigDecimal("20000"));

			// Assert
			assertThat(product.getPrice().value()).isEqualByComparingTo(new BigDecimal("20000"));
		}

	}


	@Nested
	@DisplayName("changeStock()")
	class ChangeStockTest {

		@Test
		@DisplayName("[changeStock()] 유효한 재고 -> 재고 변경 성공")
		void changeStockSuccess() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);

			// Act
			product.changeStock(200L);

			// Assert
			assertThat(product.getStock().value()).isEqualTo(200L);
		}

	}


	@Nested
	@DisplayName("changeDescription()")
	class ChangeDescriptionTest {

		@Test
		@DisplayName("[changeDescription()] 유효한 설명 -> 설명 변경 성공")
		void changeDescriptionSuccess() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, "원래 설명");

			// Act
			product.changeDescription("변경된 설명");

			// Assert
			assertThat(product.getDescription().value()).isEqualTo("변경된 설명");
		}


		@Test
		@DisplayName("[changeDescription()] null -> description null로 변경")
		void changeDescriptionToNull() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, "원래 설명");

			// Act
			product.changeDescription(null);

			// Assert
			assertThat(product.getDescription()).isNull();
		}

	}


	@Nested
	@DisplayName("decreaseStock()")
	class DecreaseStockTest {

		@Test
		@DisplayName("[decreaseStock()] 유효한 차감 -> 재고 감소")
		void decreaseStockSuccess() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);

			// Act
			product.decreaseStock(30L);

			// Assert
			assertThat(product.getStock().value()).isEqualTo(70L);
		}


		@Test
		@DisplayName("[decreaseStock()] 재고 초과 차감 -> PRODUCT_OUT_OF_STOCK 예외")
		void decreaseStockExceeds() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 10L, null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> product.decreaseStock(11L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_OUT_OF_STOCK);
		}

	}


	@Nested
	@DisplayName("delete()")
	class DeleteTest {

		@Test
		@DisplayName("[delete()] 미삭제 상품 -> deletedAt 설정")
		void deleteSuccess() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);

			// Act
			product.delete();

			// Assert
			assertAll(
				() -> assertThat(product.isDeleted()).isTrue(),
				() -> assertThat(product.getDeletedAt()).isNotNull()
			);
		}


		@Test
		@DisplayName("[delete()] 이미 삭제된 상품 -> PRODUCT_NOT_FOUND 예외")
		void deleteAlreadyDeleted() {
			// Arrange
			Product product = Product.reconstruct(
				1L, 1L,
				ProductName.from("상품"),
				Money.from(BigDecimal.TEN),
				Stock.from(100L),
				null, 0L, ZonedDateTime.now()
			);

			// Act
			CoreException exception = assertThrows(CoreException.class, product::delete);

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);
		}

	}


	@Nested
	@DisplayName("isDeleted()")
	class IsDeletedTest {

		@Test
		@DisplayName("[isDeleted()] deletedAt null -> false")
		void notDeleted() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);

			// Assert
			assertThat(product.isDeleted()).isFalse();
		}


		@Test
		@DisplayName("[isDeleted()] deletedAt 존재 -> true")
		void deleted() {
			// Arrange
			Product product = Product.reconstruct(
				1L, 1L,
				ProductName.from("상품"),
				Money.from(BigDecimal.TEN),
				Stock.from(100L),
				null, 0L, ZonedDateTime.now()
			);

			// Assert
			assertThat(product.isDeleted()).isTrue();
		}

	}

}
