package com.loopers.catalog.product.infrastructure.query;


import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import com.loopers.catalog.product.infrastructure.entity.QProductEntity;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class ProductQueryPortImpl implements ProductQueryPort {

	// querydsl
	private final JPAQueryFactory queryFactory;

	private static final QProductEntity product = QProductEntity.productEntity;


	/**
	 * 상품 복잡 조회 포트 구현체
	 * 1. 사용자 상품 검색 (활성 상품만)
	 * 2. 관리자 상품 검색 (전체 상품)
	 */

	// 1. 사용자 상품 검색 (활성 상품만)
	@Override
	public PageResult<ProductOutDto> searchProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

		// 활성 상품 조건 + 브랜드 필터
		BooleanExpression condition = product.deletedAt.isNull();
		if (criteria.brandId() != null) {
			condition = condition.and(product.brandId.eq(criteria.brandId()));
		}

		// 전체 개수 조회
		long totalElements = queryFactory
			.select(product.count())
			.from(product)
			.where(condition)
			.fetchOne();

		// 정렬 + 페이지네이션 조회
		List<ProductEntity> entities = queryFactory
			.selectFrom(product)
			.where(condition)
			.orderBy(getOrderSpecifier(criteria.sortType()))
			.offset((long) pageCriteria.page() * pageCriteria.size())
			.limit(pageCriteria.size())
			.fetch();

		// DTO 변환
		List<ProductOutDto> content = entities.stream()
			.map(entity -> new ProductOutDto(
				entity.getId(),
				entity.getBrandId(),
				null,
				entity.getName(),
				entity.getPrice(),
				entity.getStock(),
				entity.getLikeCount()
			))
			.toList();

		return new PageResult<>(content, pageCriteria.page(), pageCriteria.size(), totalElements);
	}


	// 2. 관리자 상품 검색 (전체 상품)
	@Override
	public PageResult<AdminProductOutDto> searchAdminProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

		// 브랜드 필터 (삭제된 상품 포함)
		BooleanExpression condition = null;
		if (criteria.brandId() != null) {
			condition = product.brandId.eq(criteria.brandId());
		}

		// 전체 개수 조회
		long totalElements = queryFactory
			.select(product.count())
			.from(product)
			.where(condition)
			.fetchOne();

		// 정렬 + 페이지네이션 조회
		List<ProductEntity> entities = queryFactory
			.selectFrom(product)
			.where(condition)
			.orderBy(getOrderSpecifier(criteria.sortType()))
			.offset((long) pageCriteria.page() * pageCriteria.size())
			.limit(pageCriteria.size())
			.fetch();

		// DTO 변환
		List<AdminProductOutDto> content = entities.stream()
			.map(entity -> new AdminProductOutDto(
				entity.getId(),
				entity.getBrandId(),
				null,
				entity.getName(),
				entity.getPrice(),
				entity.getStock(),
				entity.getLikeCount(),
				entity.getDeletedAt()
			))
			.toList();

		return new PageResult<>(content, pageCriteria.page(), pageCriteria.size(), totalElements);
	}


	/**
	 * private 메서드
	 * 1. 정렬 조건 변환
	 */

	// 1. 정렬 조건 변환
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
