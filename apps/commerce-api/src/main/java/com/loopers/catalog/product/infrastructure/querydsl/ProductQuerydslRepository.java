package com.loopers.catalog.product.infrastructure.querydsl;


import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.cache.dto.IdListCacheEntry;
import com.loopers.catalog.product.infrastructure.cache.dto.ProductCacheDto;
import com.loopers.catalog.product.infrastructure.entity.QProductReadModelEntity;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
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

	private static final QProductReadModelEntity readModel = QProductReadModelEntity.productReadModelEntity;


	/**
	 * 상품 QueryDSL 쿼리 리포지토리 (Read Model 기반 — JOIN 없음)
	 * 1. 사용자 상품 검색 (활성 상품만, DTO Projection)
	 * 2. 관리자 상품 검색 (전체 상품, DTO Projection)
	 * 3. 상품 ID 리스트 검색 (캐시 write-through용)
	 * 4. 단건 상품 캐시 DTO 조회 (Read Model projection)
	 * 5. 다건 상품 캐시 DTO 조회 (Read Model bulk projection)
	 * 6. 관리자 상품 상세 조회 (삭제 포함, Read Model projection)
	 */

	// 1. 사용자 상품 검색 (활성 상품만, DTO Projection — Read Model 단일 테이블 조회)
	public PageResult<ProductOutDto> searchProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

		// 활성 상품 조건 + 브랜드 필터
		BooleanExpression condition = readModel.deletedAt.isNull();
		if (criteria.brandId() != null) {
			condition = condition.and(readModel.brandId.eq(criteria.brandId()));
		}

		// 정렬 조건 (tie-breaker 포함)
		OrderSpecifier<?>[] orders = getOrderSpecifiers(criteria.sortType());

		// 전체 개수 조회
		long totalElements = countProducts(condition);

		// DTO Projection 직접 조회 (Read Model — JOIN 없음)
		long offset = (long) pageCriteria.page() * pageCriteria.size();
		List<ProductOutDto> content = queryFactory
			.select(Projections.constructor(ProductOutDto.class,
				readModel.id, readModel.brandId, readModel.brandName,
				readModel.name, readModel.price, readModel.stock, readModel.likeCount))
			.from(readModel)
			.where(condition)
			.orderBy(orders)
			.offset(offset)
			.limit(pageCriteria.size())
			.fetch();

		return new PageResult<>(content, pageCriteria.page(), pageCriteria.size(), totalElements);
	}


	// 2. 관리자 상품 검색 (전체 상품, DTO Projection — Read Model 단일 테이블 조회)
	public PageResult<AdminProductOutDto> searchAdminProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

		// 브랜드 필터 (삭제된 상품 포함)
		BooleanExpression condition = null;
		if (criteria.brandId() != null) {
			condition = readModel.brandId.eq(criteria.brandId());
		}

		// 정렬 조건 (tie-breaker 포함)
		OrderSpecifier<?>[] orders = getOrderSpecifiers(criteria.sortType());

		// 전체 개수 조회
		long totalElements = countProducts(condition);

		// DTO Projection 직접 조회 (Read Model — JOIN 없음)
		long offset = (long) pageCriteria.page() * pageCriteria.size();
		List<AdminProductOutDto> content = queryFactory
			.select(Projections.constructor(AdminProductOutDto.class,
				readModel.id, readModel.brandId, readModel.brandName,
				readModel.name, readModel.price, readModel.stock, readModel.likeCount,
				readModel.deletedAt))
			.from(readModel)
			.where(condition)
			.orderBy(orders)
			.offset(offset)
			.limit(pageCriteria.size())
			.fetch();

		return new PageResult<>(content, pageCriteria.page(), pageCriteria.size(), totalElements);
	}


	// 3. 상품 ID 리스트 검색 (캐시 write-through용, 활성 상품만)
	public IdListCacheEntry searchProductIds(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

		// 활성 상품 조건 + 브랜드 필터
		BooleanExpression condition = readModel.deletedAt.isNull();
		if (criteria.brandId() != null) {
			condition = condition.and(readModel.brandId.eq(criteria.brandId()));
		}

		// 전체 개수 조회
		long totalElements = countProducts(condition);

		// ID 목록 (정렬 + tie-breaker + 페이지네이션)
		long offset = (long) pageCriteria.page() * pageCriteria.size();
		List<Long> ids = queryFactory.select(readModel.id)
			.from(readModel)
			.where(condition)
			.orderBy(getOrderSpecifiers(criteria.sortType()))
			.offset(offset)
			.limit(pageCriteria.size())
			.fetch();

		return new IdListCacheEntry(ids, totalElements);
	}


	// 4. 단건 상품 캐시 DTO 조회 (Read Model projection, 활성 상품만)
	public ProductCacheDto findProductCacheDtoById(Long productId) {

		return queryFactory.select(Projections.constructor(ProductCacheDto.class,
				readModel.id, readModel.brandId, readModel.brandName, readModel.name,
				readModel.price, readModel.stock, readModel.description, readModel.likeCount))
			.from(readModel)
			.where(readModel.id.eq(productId).and(readModel.deletedAt.isNull()))
			.fetchOne();
	}


	// 5. 다건 상품 캐시 DTO 조회 (Read Model bulk projection, 활성 상품만)
	public List<ProductCacheDto> findProductCacheDtosByIds(List<Long> productIds) {

		return queryFactory.select(Projections.constructor(ProductCacheDto.class,
				readModel.id, readModel.brandId, readModel.brandName, readModel.name,
				readModel.price, readModel.stock, readModel.description, readModel.likeCount))
			.from(readModel)
			.where(readModel.id.in(productIds).and(readModel.deletedAt.isNull()))
			.fetch();
	}


	// 6. 관리자 상품 상세 조회 (삭제 포함, Read Model projection — 브랜드 삭제 시에도 비정규화된 brandName 사용)
	public AdminProductDetailOutDto findAdminProductDetailById(Long productId) {

		return queryFactory.select(Projections.constructor(AdminProductDetailOutDto.class,
				readModel.id, readModel.brandId, readModel.brandName, readModel.name,
				readModel.price, readModel.stock, readModel.description, readModel.likeCount,
				readModel.deletedAt))
			.from(readModel)
			.where(readModel.id.eq(productId))
			.fetchOne();
	}


	/**
	 * private 메서드
	 * - 전체 개수 조회
	 * - 정렬 조건 변환 (tie-breaker 포함)
	 */

	// 전체 개수 조회
	private long countProducts(BooleanExpression condition) {
		Long count = queryFactory
			.select(readModel.count())
			.from(readModel)
			.where(condition)
			.fetchOne();
		return count != null ? count : 0L;
	}


	// 정렬 조건 변환 (tie-breaker: 동률 시 id 내림차순)
	private OrderSpecifier<?>[] getOrderSpecifiers(ProductSortType sortType) {
		OrderSpecifier<?> primary;
		if (sortType == null) {
			primary = readModel.createdAt.desc();
		} else {
			primary = switch (sortType) {
				case LATEST -> readModel.createdAt.desc();
				case PRICE_ASC -> readModel.price.asc();
				case LIKES_DESC -> readModel.likeCount.desc();
			};
		}
		// tie-breaker: 동률 시 id 내림차순 (최신 상품 우선, 페이지 경계 안정화)
		OrderSpecifier<?> secondary = readModel.id.desc();
		return new OrderSpecifier<?>[]{ primary, secondary };
	}

}
