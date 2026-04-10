package com.loopers.ranking.application.facade;


import com.loopers.ranking.application.dto.in.AdminRankingRebuildInDto;
import com.loopers.ranking.application.dto.out.AdminRankingRebuildOutDto;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;


/**
 * 랭킹 재계산 파사드
 * - commerce-api는 batch job을 직접 실행할 수 없으므로 파라미터 검증 후 ACCEPTED 응답
 * - 실제 재계산은 commerce-batch: job.name=rankingRebuildJob 으로 실행
 *
 * 1. 재계산 요청 검증 및 ACCEPTED 응답 반환
 */
@Service
@Slf4j
public class RankingRebuildFacade {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
	private static final String DEFAULT_SCORER_TYPE = "SATURATION";
	private static final double DEFAULT_CARRY_OVER_WEIGHT = 0.1;


	// 1. 재계산 요청 검증 및 ACCEPTED 응답 반환
	public AdminRankingRebuildOutDto requestRebuild(AdminRankingRebuildInDto inDto) {
		// from/to 날짜 파싱 검증 (yyyyMMdd 형식)
		LocalDate from = parseDate(inDto.from());
		LocalDate to = parseDate(inDto.to());

		// from ≤ to 검증
		if (from.isAfter(to)) {
			throw new CoreException(ErrorType.BAD_REQUEST, "from(" + inDto.from() + ")이 to(" + inDto.to() + ")보다 큽니다.");
		}

		// scorerType 기본값 적용
		String scorerType = (inDto.scorerType() != null && !inDto.scorerType().isBlank())
			? inDto.scorerType()
			: DEFAULT_SCORER_TYPE;

		// carryOverWeight 기본값 적용
		double carryOverWeight = inDto.carryOverWeight() > 0
			? inDto.carryOverWeight()
			: DEFAULT_CARRY_OVER_WEIGHT;

		log.info("[RankingRebuild] ACCEPTED: from={}, to={}, scorer={}, carryWeight={}",
			inDto.from(), inDto.to(), scorerType, carryOverWeight);

		return new AdminRankingRebuildOutDto(inDto.from(), inDto.to(), scorerType, carryOverWeight);
	}


	// 날짜 파싱 (yyyyMMdd)
	private LocalDate parseDate(String dateStr) {
		try {
			return LocalDate.parse(dateStr, DATE_FORMAT);
		} catch (DateTimeParseException e) {
			throw new CoreException(ErrorType.BAD_REQUEST, "날짜 형식이 올바르지 않습니다 (yyyyMMdd): " + dateStr);
		}
	}

}
