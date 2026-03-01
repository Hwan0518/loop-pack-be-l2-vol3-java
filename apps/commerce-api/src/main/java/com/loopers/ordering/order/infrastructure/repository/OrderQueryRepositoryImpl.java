package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.repository.OrderQueryRepository;
import com.loopers.ordering.order.domain.repository.vo.PageCriteria;
import com.loopers.ordering.order.domain.repository.vo.PageResult;
import com.loopers.ordering.order.infrastructure.entity.OrderEntity;
import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import com.loopers.ordering.order.infrastructure.jpa.OrderItemJpaRepository;
import com.loopers.ordering.order.infrastructure.jpa.OrderJpaRepository;
import com.loopers.ordering.order.infrastructure.mapper.OrderEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Repository
@RequiredArgsConstructor
public class OrderQueryRepositoryImpl implements OrderQueryRepository {

	// jpa
	private final OrderJpaRepository orderJpaRepository;
	private final OrderItemJpaRepository orderItemJpaRepository;
	// mapper
	private final OrderEntityMapper orderMapper;


	/**
	 * 주문 조회 리포지토리 구현체
	 * 1. ID로 주문 조회
	 * 2. 사용자별 주문 목록 조회 (페이지네이션)
	 * 3. 전체 주문 목록 조회 (관리자, 페이지네이션)
	 */

	// 1. ID로 주문 조회
	@Override
	public Optional<Order> findById(Long id) {
		return orderJpaRepository.findById(id)
			.map(orderEntity -> {
				List<OrderItemEntity> itemEntities = orderItemJpaRepository.findByOrderId(orderEntity.getId());
				return orderMapper.toDomain(orderEntity, itemEntities);
			});
	}


	// 2. 사용자별 주문 목록 조회 (페이지네이션, 날짜 필터)
	@Override
	public PageResult<Order> findByUserId(Long userId, LocalDate startDate, LocalDate endDate, PageCriteria pageCriteria) {

		// 페이지 요청 생성
		var pageable = PageRequest.of(
			pageCriteria.page(),
			pageCriteria.size(),
			Sort.by(Sort.Direction.DESC, "id")
		);

		// 주문 조회 (날짜 필터 적용 여부에 따라 분기)
		Page<OrderEntity> page;
		if (startDate != null && endDate != null) {
			// 날짜 범위 변환: startDate 00:00:00 ~ endDate+1 00:00:00
			ZonedDateTime startDateTime = startDate.atStartOfDay(ZoneId.systemDefault());
			ZonedDateTime endDateTime = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault());
			page = orderJpaRepository.findByUserIdAndCreatedAtInRange(userId, startDateTime, endDateTime, pageable);
		} else {
			page = orderJpaRepository.findByUserId(userId, pageable);
		}

		// 주문 항목 일괄 조회
		List<Order> orders = loadOrdersWithItems(page.getContent());

		return new PageResult<>(orders, page.getNumber(), page.getSize(), page.getTotalElements());
	}


	// 3. 전체 주문 목록 조회 (관리자, 페이지네이션)
	@Override
	public PageResult<Order> findAll(PageCriteria pageCriteria) {

		// 페이지 요청 생성
		var pageable = PageRequest.of(
			pageCriteria.page(),
			pageCriteria.size(),
			Sort.by(Sort.Direction.DESC, "id")
		);

		// 주문 조회
		Page<OrderEntity> page = orderJpaRepository.findAll(pageable);

		// 주문 항목 일괄 조회
		List<Order> orders = loadOrdersWithItems(page.getContent());

		return new PageResult<>(orders, page.getNumber(), page.getSize(), page.getTotalElements());
	}


	/**
	 * private 메서드
	 * 1. 주문 목록에 주문 항목 일괄 로딩
	 */

	// 1. 주문 목록에 주문 항목 일괄 로딩
	private List<Order> loadOrdersWithItems(List<OrderEntity> orderEntities) {

		// 주문 ID 목록 추출
		List<Long> orderIds = orderEntities.stream()
			.map(OrderEntity::getId)
			.toList();

		// 주문 항목 일괄 조회 후 주문 ID별 그룹화
		Map<Long, List<OrderItemEntity>> itemsByOrderId = orderItemJpaRepository.findByOrderIdIn(orderIds).stream()
			.collect(Collectors.groupingBy(OrderItemEntity::getOrderId));

		// 주문별 항목 매핑 후 도메인 변환
		return orderEntities.stream()
			.map(orderEntity -> {
				List<OrderItemEntity> items = itemsByOrderId.getOrDefault(orderEntity.getId(), List.of());
				return orderMapper.toDomain(orderEntity, items);
			})
			.toList();
	}

}
