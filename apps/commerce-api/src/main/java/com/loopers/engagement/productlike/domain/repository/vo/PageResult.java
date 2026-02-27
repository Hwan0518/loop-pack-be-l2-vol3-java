package com.loopers.engagement.productlike.domain.repository.vo;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
}
