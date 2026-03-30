package com.loopers.logging.application.port.out;


import com.loopers.logging.application.dto.EventLogData;

import java.util.List;


/**
 * 이벤트 로그 저장 포트 (application → infrastructure 계약)
 * - 배치 로그 저장
 */
public interface EventLogPort {

	// 1. 성공 로그 일괄 저장
	void saveAll(List<EventLogData> logs);

}
