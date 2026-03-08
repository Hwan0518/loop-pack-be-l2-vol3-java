package com.loopers.engagement.brandlike.application.service;

import com.loopers.engagement.brandlike.application.port.out.client.catalog.BrandLikeTargetValidator;
import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.domain.repository.BrandLikeCommandRepository;
import com.loopers.engagement.brandlike.domain.repository.BrandLikeQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class BrandLikeCommandService {

	// repository
	private final BrandLikeCommandRepository brandLikeCommandRepository;
	private final BrandLikeQueryRepository brandLikeQueryRepository;
	// port
	private final BrandLikeTargetValidator brandLikeTargetValidator;


	/**
	 * 브랜드 좋아요 명령 서비스
	 * 1. 좋아요 조회
	 * 2. 브랜드 좋아요 생성
	 * 3. 브랜드 좋아요 삭제
	 * 4. 브랜드 ID로 브랜드 좋아요 전체 삭제
	 */

	// 1. 좋아요 조회
	@Transactional(readOnly = true)
	public Optional<BrandLike> findLike(Long userId, Long targetId) {
		return brandLikeQueryRepository.findByUserIdAndTargetId(userId, targetId);
	}


	// 2. 브랜드 좋아요 생성
	@Transactional
	public BrandLike createLike(Long userId, Long targetId) {

		// 좋아요 대상 브랜드 존재 여부 검증 (Provider 예외 → Consumer 에러 매핑)
		try {
			brandLikeTargetValidator.validate(targetId);
		} catch (CoreException e) {
			throw new CoreException(ErrorType.LIKE_TARGET_NOT_FOUND);
		}

		// 좋아요 생성 및 저장
		BrandLike brandLike = BrandLike.create(userId, targetId);
		return brandLikeCommandRepository.save(brandLike);
	}


	// 3. 브랜드 좋아요 삭제
	@Transactional
	public void deleteLike(Long userId, Long targetId) {

		// 좋아요 조회
		BrandLike brandLike = brandLikeQueryRepository
			.findByUserIdAndTargetId(userId, targetId)
			.orElseThrow(() -> new CoreException(ErrorType.LIKE_NOT_FOUND));

		// 삭제
		brandLikeCommandRepository.delete(brandLike);
	}


	// 4. 브랜드 ID로 브랜드 좋아요 전체 삭제
	@Transactional
	public void deleteAllByTargetId(Long targetId) {
		brandLikeCommandRepository.deleteAllByTargetId(targetId);
	}

}
