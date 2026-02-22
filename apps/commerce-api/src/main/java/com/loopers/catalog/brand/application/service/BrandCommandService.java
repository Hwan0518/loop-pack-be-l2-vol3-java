package com.loopers.catalog.brand.application.service;


import com.loopers.catalog.brand.application.dto.in.BrandCreateInDto;
import com.loopers.catalog.brand.application.dto.in.BrandUpdateInDto;
import com.loopers.catalog.brand.application.dto.in.BrandVisibleStatusUpdateInDto;
import com.loopers.catalog.brand.domain.event.BrandDeletedEvent;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.brand.domain.repository.BrandCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BrandCommandService {

	// repository
	private final BrandCommandRepository brandCommandRepository;
	// event
	private final ApplicationEventPublisher eventPublisher;


	/**
	 * 브랜드 명령 서비스
	 * 1. 브랜드 생성
	 * 2. 브랜드 수정
	 * 3. 브랜드 삭제
	 * 4. 노출 상태 변경
	 */

	// 1. 브랜드 생성
	@Transactional
	public Brand createBrand(BrandCreateInDto inDto) {

		// InDto → VO 생성
		BrandName name = BrandName.create(inDto.name());
		BrandDescription description = BrandDescription.create(inDto.description());

		// 브랜드 생성
		Brand brand = Brand.create(name, description);

		// 저장
		return brandCommandRepository.save(brand);
	}


	// 2. 브랜드 수정
	@Transactional
	public Brand updateBrand(Brand brand, BrandUpdateInDto inDto) {

		// 브랜드명 변경
		brand.changeName(BrandName.create(inDto.name()));

		// 설명 변경
		brand.changeDescription(BrandDescription.create(inDto.description()));

		// 노출 상태 변경 (null이 아닌 경우에만)
		if (inDto.visibleStatus() != null) {
			brand.changeVisibleStatus(inDto.visibleStatus());
		}

		// 저장
		return brandCommandRepository.save(brand);
	}


	// 3. 브랜드 삭제
	@Transactional
	public void deleteBrand(Brand brand, boolean hasActiveProducts) {

		// 삭제 가능 여부 검증
		brand.validateDeletable(hasActiveProducts);

		// 삭제
		brandCommandRepository.delete(brand);

		// 삭제 이벤트 발행
		eventPublisher.publishEvent(new BrandDeletedEvent(brand.getId()));
	}


	// 4. 노출 상태 변경
	@Transactional
	public Brand updateVisibleStatus(Brand brand, BrandVisibleStatusUpdateInDto inDto) {

		// 노출 상태 변경
		brand.changeVisibleStatus(inDto.visibleStatus());

		// 저장
		return brandCommandRepository.save(brand);
	}

}
