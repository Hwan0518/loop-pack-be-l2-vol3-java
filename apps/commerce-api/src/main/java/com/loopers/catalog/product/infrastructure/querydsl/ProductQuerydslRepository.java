package com.loopers.catalog.product.infrastructure.querydsl;


import com.loopers.catalog.brand.infrastructure.entity.QBrandEntity;
import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import com.loopers.catalog.product.infrastructure.entity.QProductEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class ProductQuerydslRepository {

	// querydsl
	private final JPAQueryFactory queryFactory;

	private static final QProductEntity product = QProductEntity.productEntity;
	private static final QBrandEntity brand = QBrandEntity.brandEntity;


	/**
	 * 상품 QueryDSL 쿼리 리포지토리
	 * 1. 사용자 상품 검색 (활성 상품만)
	 * 2. 관리자 상품 검색 (전체 상품)
	 */

	// 1. 사용자 상품 검색 (활성 상품만)
	public PageResult<ProductOutDto> searchProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

		// 활성 상품 조건 + 브랜드 필터
		BooleanExpression condition = product.deletedAt.isNull();
		if (criteria.brandId() != null) {
			condition = condition.and(product.brandId.eq(criteria.brandId()));
		}

		// 정렬 조건
		OrderSpecifier<?> order = getOrderSpecifier(criteria.sortType());

		// 전체 개수 조회
		long totalElements = countProducts(condition);

		// 상품 + 브랜드명 조회
		long offset = (long) pageCriteria.page() * pageCriteria.size();
		List<Tuple> tuples = fetchProductsWithBrand(condition, order, offset, pageCriteria.size());

		// DTO 변환
		List<ProductOutDto> content = tuples.stream()
			.map(tuple -> {
				ProductEntity entity = tuple.get(product);
				String brandName = tuple.get(1, String.class);
				return new ProductOutDto(
					entity.getId(),
					entity.getBrandId(),
					brandName,
					entity.getName(),
					entity.getPrice(),
					entity.getStock(),
					entity.getLikeCount()
				);
			})
			.toList();

		return new PageResult<>(content, pageCriteria.page(), pageCriteria.size(), totalElements);
	}


	// 2. 관리자 상품 검색 (전체 상품)
	public PageResult<AdminProductOutDto> searchAdminProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

		// 브랜드 필터 (삭제된 상품 포함)
		BooleanExpression condition = null;
		if (criteria.brandId() != null) {
			condition = product.brandId.eq(criteria.brandId());
		}

		// 정렬 조건
		OrderSpecifier<?> order = getOrderSpecifier(criteria.sortType());

		// 전체 개수 조회
		long totalElements = countProducts(condition);

		// 상품 + 브랜드명 조회
		long offset = (long) pageCriteria.page() * pageCriteria.size();
		List<Tuple> tuples = fetchProductsWithBrand(condition, order, offset, pageCriteria.size());

		// DTO 변환
		List<AdminProductOutDto> content = tuples.stream()
			.map(tuple -> {
				ProductEntity entity = tuple.get(product);
				String brandName = tuple.get(1, String.class);
				return new AdminProductOutDto(
					entity.getId(),
					entity.getBrandId(),
					brandName,
					entity.getName(),
					entity.getPrice(),
					entity.getStock(),
					entity.getLikeCount(),
					entity.getDeletedAt()
				);
			})
			.toList();

		return new PageResult<>(content, pageCriteria.page(), pageCriteria.size(), totalElements);
	}


	/**
	 * private 메서드
	 * 1. 상품 + 브랜드명 조회 (Brand LEFT JOIN)
	 * 2. 전체 개수 조회
	 * 3. 정렬 조건 변환
	 */

	// 1. 상품 + 브랜드명 조회 (Brand LEFT JOIN)
	private List<Tuple> fetchProductsWithBrand(BooleanExpression condition, OrderSpecifier<?> order,
		long offset, int limit) {
		return queryFactory
			.select(product, brand.name)
			.from(product)
			.leftJoin(brand).on(brand.id.eq(product.brandId))
			.where(condition)
			.orderBy(order)
			.offset(offset)
			.limit(limit)
			.fetch();
	}


	// 2. 전체 개수 조회
	private long countProducts(BooleanExpression condition) {
		Long count = queryFactory
			.select(product.count())
			.from(product)
			.where(condition)
			.fetchOne();
		return count != null ? count : 0L;
	}


	// 3. 정렬 조건 변환
	private OrderSpecifier<?> getOrderSpecifier(ProductSortType sortType) {
		if (sortType == null) {
			return product.createdAt.desc();
		}
		return switch (sortType) {
			case LATEST -> product.createdAt.desc();
			case PRICE_ASC -> product.price.asc();
			case LIKES_DESC -> product.likeCount.desc();
		};
	}

}
