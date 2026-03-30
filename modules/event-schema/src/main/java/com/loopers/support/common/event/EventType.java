package com.loopers.support.common.event;


/**
 * Kafka 이벤트 타입 — aggregateType + eventType + topic 매핑
 * - Producer: outboxEventPort.save(EventType, ...) 에서 사용
 * - Consumer: eventType 문자열 비교 대신 enum 참조
 */
public enum EventType {

	// catalog-events
	PRODUCT_LIKED("PRODUCT", "catalog-events"),
	PRODUCT_UNLIKED("PRODUCT", "catalog-events"),
	PRODUCT_VIEWED("PRODUCT", "catalog-events"),

	// order-events
	ORDER_CREATED("ORDER", "order-events"),
	ORDER_PAID("ORDER", "order-events"),

	// coupon-issue-requests
	COUPON_ISSUE_REQUESTED("COUPON", "coupon-issue-requests"),

	// product-metrics-snapshots
	PRODUCT_METRICS_UPDATED("PRODUCT", "product-metrics-snapshots");


	private final String aggregateType;
	private final String topic;


	EventType(String aggregateType, String topic) {
		this.aggregateType = aggregateType;
		this.topic = topic;
	}


	public String getAggregateType() {
		return aggregateType;
	}

	public String getTopic() {
		return topic;
	}

	public String getEventType() {
		return name();
	}

}
