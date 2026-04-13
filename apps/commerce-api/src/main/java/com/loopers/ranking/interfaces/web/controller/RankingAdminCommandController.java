package com.loopers.ranking.interfaces.web.controller;


import com.loopers.ranking.application.dto.in.AdminRankingRebuildInDto;
import com.loopers.ranking.application.dto.out.AdminRankingRebuildOutDto;
import com.loopers.ranking.application.facade.RankingRebuildFacade;
import com.loopers.ranking.interfaces.web.request.AdminRankingRebuildRequest;
import com.loopers.ranking.interfaces.web.response.AdminRankingRebuildResponse;
import com.loopers.support.common.AdminHeaderValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/rankings")
@RequiredArgsConstructor
public class RankingAdminCommandController {

	// facade
	private final RankingRebuildFacade rankingRebuildFacade;


	/**
	 * 랭킹 관리자 명령 컨트롤러 (LDAP 인증 필요)
	 * 1. 랭킹 재계산 요청 (ACCEPTED — 실제 실행은 commerce-batch)
	 */

	// 1. 랭킹 재계산 요청
	@PostMapping("/rebuild")
	public ResponseEntity<AdminRankingRebuildResponse> requestRebuild(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@Valid @RequestBody AdminRankingRebuildRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성 (기본값 처리는 Facade에서)
		AdminRankingRebuildInDto inDto = new AdminRankingRebuildInDto(
			request.from(),
			request.to(),
			request.scorerType(),
			request.carryOverWeight() != null ? request.carryOverWeight() : 0.0
		);

		// 재계산 요청 처리
		AdminRankingRebuildOutDto outDto = rankingRebuildFacade.requestRebuild(inDto);

		// 응답 변환
		AdminRankingRebuildResponse response = AdminRankingRebuildResponse.accepted(
			outDto.from(), outDto.to(), outDto.scorerType(), outDto.carryOverWeight()
		);

		// 202 Accepted 반환
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

}
