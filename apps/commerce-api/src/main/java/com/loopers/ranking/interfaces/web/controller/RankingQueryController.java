package com.loopers.ranking.interfaces.web.controller;


import com.loopers.ranking.application.dto.out.RankingPageOutDto;
import com.loopers.ranking.application.facade.RankingQueryFacade;
import com.loopers.ranking.interfaces.web.response.RankingPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingQueryController {

	// facade
	private final RankingQueryFacade rankingQueryFacade;


	/**
	 * 랭킹 조회 컨트롤러
	 * 1. 랭킹 목록 조회 (인증 불필요)
	 */

	// 1. 랭킹 목록 조회
	@GetMapping
	public ResponseEntity<RankingPageResponse> getRankings(
		@RequestParam(required = false) String date,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// 입력 보정 (음수 page, 0 이하 size 방어)
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));

		// 랭킹 조회
		RankingPageOutDto result = rankingQueryFacade.getRankings(date, safePage, safeSize);

		// 응답 변환
		RankingPageResponse response = RankingPageResponse.from(result);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
