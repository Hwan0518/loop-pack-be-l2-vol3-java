package com.loopers.ordering.order.interfaces.web.response;


import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 주문 상세 조회 응답
 * - id: 주문 ID
 * - userId: 주문자 ID
 * - originalTotalPrice: 쿠폰 적용 전 총액
 * - discountAmount: 할인 금액
 * - totalPrice: 최종 결제 금액
 * - status: 주문 상태
 * - items: 주문 항목 목록
 * - couponSnapshot: 적용된 쿠폰 스냅샷 (nullable)
 * - createdAt: 주문 생성 일시
 */
public record OrderDetailResponse(
	Long id,
	Long userId,
	BigDecimal originalTotalPrice,
	BigDecimal discountAmount,
	BigDecimal totalPrice,
	String status,
	List<OrderItemResponse> items,
	CouponSnapshotDto couponSnapshot,
	LocalDateTime createdAt
) {

	/**
	 * 쿠폰 스냅샷 응답 DTO (Response 내부 record — 응답 레이어 전용)
	 * - issuedCouponId: 발급 쿠폰 ID
	 * - name: 쿠폰 이름 스냅샷
	 * - type: 쿠폰 타입 스냅샷
	 * - value: 할인 값 스냅샷
	 */
	public record CouponSnapshotDto(
		Long issuedCouponId,
		String name,
		String type,
		BigDecimal value
	) {

		// 1. OutDto의 CouponSnapshotDto를 Response의 CouponSnapshotDto로 변환
		public static CouponSnapshotDto from(OrderDetailOutDto.CouponSnapshotDto dto) {
			if (dto == null) {
				return null;
			}
			return new CouponSnapshotDto(
				dto.issuedCouponId(),
				dto.name(),
				dto.type(),
				dto.value()
			);
		}

	}


	// 1. OrderDetailOutDto를 응답 객체로 변환
	public static OrderDetailResponse from(OrderDetailOutDto outDto) {
		return new OrderDetailResponse(
			outDto.id(),
			outDto.userId(),
			outDto.originalTotalPrice(),
			outDto.discountAmount(),
			outDto.totalPrice(),
			outDto.status().name(),
			outDto.items().stream().map(OrderItemResponse::from).toList(),
			CouponSnapshotDto.from(outDto.couponSnapshot()),
			outDto.createdAt()
		);
	}

}
