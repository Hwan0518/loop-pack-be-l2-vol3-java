package com.loopers.payment.payment.interfaces.web.response;


import com.loopers.payment.payment.application.dto.out.PaymentOutDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 결제 응답
 * - id: 결제 ID
 * - userId: 결제 요청 사용자 ID
 * - orderId: 주문 ID
 * - cardType: 카드 타입
 * - cardNo: 카드번호 (마스킹)
 * - amount: 결제 금액
 * - transactionKey: PG 트랜잭션 키
 * - status: 결제 상태
 * - failureReason: 결제 실패 사유
 * - createdAt: 결제 생성 일시
 */
public record PaymentResponse(
	Long id,
	Long userId,
	Long orderId,
	String cardType,
	String cardNo,
	BigDecimal amount,
	String transactionKey,
	String status,
	String failureReason,
	LocalDateTime createdAt
) {

	public static PaymentResponse from(PaymentOutDto outDto) {
		return new PaymentResponse(
			outDto.id(),
			outDto.userId(),
			outDto.orderId(),
			outDto.cardType() != null ? outDto.cardType().name() : null,
			maskCardNo(outDto.cardNo()),
			outDto.amount(),
			outDto.transactionKey(),
			outDto.status() != null ? outDto.status().name() : null,
			outDto.failureReason(),
			outDto.createdAt()
		);
	}

	// 카드번호 마스킹 (1234-****-****-3456)
	private static String maskCardNo(String cardNo) {
		if (cardNo == null || cardNo.length() < 19) {
			return cardNo;
		}
		return cardNo.substring(0, 4) + "-****-****-" + cardNo.substring(15);
	}

}
