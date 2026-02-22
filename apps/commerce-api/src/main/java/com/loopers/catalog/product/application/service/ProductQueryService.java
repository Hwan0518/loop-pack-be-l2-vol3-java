package com.loopers.catalog.product.application.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ProductQueryService {

	/**
	 * 상품 조회 서비스 (stub)
	 * - Product BC 미구현 상태이므로 stub으로 제공
	 * 1. 브랜드의 활성 상품 존재 여부 확인
	 */

	// 1. 브랜드의 활성 상품 존재 여부 확인 (stub: 항상 false 반환)
	public boolean existsActiveByBrandId(Long brandId) {
		return false;
	}

}
