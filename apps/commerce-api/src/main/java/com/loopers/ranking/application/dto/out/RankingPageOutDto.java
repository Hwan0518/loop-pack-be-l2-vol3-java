package com.loopers.ranking.application.dto.out;


import java.util.List;


/**
 * 랭킹 페이지 조회 결과 DTO
 */
public record RankingPageOutDto(List<RankingItemOutDto> content, int page, int size, long totalElements) {

}
