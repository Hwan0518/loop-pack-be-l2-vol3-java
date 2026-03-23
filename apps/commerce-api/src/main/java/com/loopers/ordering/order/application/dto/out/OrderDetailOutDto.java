package com.loopers.ordering.order.application.dto.out;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.enums.OrderStatus;
import com.loopers.ordering.order.domain.model.vo.CouponSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 주문 상세 조회 결과 DTO
 * - id: 주문 ID
 * - userId: 주문자 ID
 * - originalTotalPrice: 쿠폰 적용 전 총액
 * - discountAmount: 할인 금액
 * - totalPrice: 최종 결제 금액
 * - items: 주문 항목 목록
 * - couponSnapshot: 적용된 쿠폰 스냅샷 (nullable)
 * - createdAt: 주문 생성 일시
 */
public record OrderDetailOutDto(
	Long id,
	Long userId,
	BigDecimal originalTotalPrice,
	BigDecimal discountAmount,
	BigDecimal totalPrice,
	OrderStatus status,
	List<OrderItemOutDto> items,
	CouponSnapshotDto couponSnapshot,
	LocalDateTime createdAt
) {

	/**
	 * 쿠폰 스냅샷 DTO (OutDto 내부 record — Domain VO 직접 노출 방지)
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

		// 1. CouponSnapshot VO를 DTO로 변환
		public static CouponSnapshotDto from(CouponSnapshot couponSnapshot) {
			if (couponSnapshot == null) {
				return null;
			}
			return new CouponSnapshotDto(
				couponSnapshot.issuedCouponId(),
				couponSnapshot.name(),
				couponSnapshot.type(),
				couponSnapshot.value()
			);
		}

	}


	// 1. Order 도메인 객체를 상세 DTO로 변환
	public static OrderDetailOutDto from(Order order) {
		return new OrderDetailOutDto(
			order.getId(),
			order.getUserId(),
			order.getOriginalTotalPrice(),
			order.getDiscountAmount(),
			order.getTotalPrice(),
			order.getStatus(),
			order.getItems().stream().map(OrderItemOutDto::from).toList(),
			CouponSnapshotDto.from(order.getCouponSnapshot()),
			order.getCreatedAt()
		);
	}

}
