package com.loopers.payment.payment.domain.model.enums;


/**
 * 결제 상태
 * - REQUESTED: 결제 요청됨
 * - SUCCESS: 결제 성공 (최종 상태)
 * - FAILED: 결제 실패 (최종 상태)
 */
public enum PaymentStatus {

	REQUESTED,
	SUCCESS,
	FAILED;


	/**
	 * 도메인 로직
	 * 1. 상태 전이 가능 여부 확인
	 */

	// 1. 상태 전이 가능 여부 확인
	public boolean canTransitionTo(PaymentStatus target) {
		if (this != REQUESTED) {
			return false;
		}
		return target == SUCCESS || target == FAILED;
	}

}
