package com.loopers.engagement.brandlike.domain.repository.vo;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
}
