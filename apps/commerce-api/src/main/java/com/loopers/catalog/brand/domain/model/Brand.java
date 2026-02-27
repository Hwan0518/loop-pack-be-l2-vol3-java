package com.loopers.catalog.brand.domain.model;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Objects;


@Getter
public class Brand {

	/**
	 * 브랜드
	 * - id: 브랜드 ID
	 * - name: 브랜드명
	 * - description: 브랜드 설명
	 * - visibleStatus: 노출 상태 (VISIBLE/HIDDEN)
	 * - deletedAt: 삭제 일시 (soft delete)
	 */

	private final Long id;
	private BrandName name;
	private BrandDescription description;
	private VisibleStatus visibleStatus;
	private ZonedDateTime deletedAt;


	// 생성자
	private Brand(Long id, BrandName name, BrandDescription description, VisibleStatus visibleStatus, ZonedDateTime deletedAt) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.visibleStatus = visibleStatus;
		this.deletedAt = deletedAt;
	}


	/**
	 * 도메인 로직
	 * 1. 브랜드 생성
	 * 2. 브랜드 재생성 (Entity -> Model 매핑용도)
	 * 3. 브랜드명 변경
	 * 4. 브랜드 설명 변경
	 * 5. 노출 상태 변경
	 * 6. 노출 상태 확인
	 * 7. 삭제 가능 여부 검증
	 */

	// 1. 브랜드 생성 (신규 브랜드 생성, 기본값 HIDDEN)
	public static Brand create(BrandName name, BrandDescription description) {

		// name null 검증
		Objects.requireNonNull(name, "name");

		// 브랜드 생성 (기본 노출 상태: HIDDEN)
		return new Brand(null, name, description, VisibleStatus.HIDDEN, null);
	}


	// 2. 브랜드 재생성 (Entity -> Model 매핑용도)
	public static Brand reconstruct(Long id, BrandName name, BrandDescription description,
		VisibleStatus visibleStatus, ZonedDateTime deletedAt) {
		return new Brand(id, name, description, visibleStatus, deletedAt);
	}


	// 3. 브랜드명 변경
	public void changeName(BrandName name) {
		this.name = Objects.requireNonNull(name, "name");
	}


	// 4. 브랜드 설명 변경
	public void changeDescription(BrandDescription description) {
		this.description = description;
	}


	// 5. 노출 상태 변경
	public void changeVisibleStatus(VisibleStatus visibleStatus) {

		// null 검증
		if (visibleStatus == null) {
			throw new CoreException(ErrorType.INVALID_BRAND_VISIBLE_STATUS);
		}

		this.visibleStatus = visibleStatus;
	}


	// 6. 노출 상태 확인
	public boolean isVisible() {
		return this.visibleStatus == VisibleStatus.VISIBLE;
	}


	// 7. 삭제 가능 여부 검증
	public void validateDeletable(boolean hasActiveProducts) {

		// 노출 중인 브랜드는 삭제 불가
		if (isVisible()) {
			throw new CoreException(ErrorType.BRAND_IS_VISIBLE);
		}

		// 활성 상품 존재 시 삭제 불가
		if (hasActiveProducts) {
			throw new CoreException(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS);
		}
	}

}
