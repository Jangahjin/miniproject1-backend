package com.pharmaprice.common.dto;

import java.util.List;

/** 목록 API 공통 페이지네이션 래퍼 (docs/API.md §1.1). */
public record PageResponse<T>(
		List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {

	public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
		int totalPages = (int) Math.ceil((double) totalElements / size);
		boolean hasNext = (long) (page + 1) * size < totalElements;
		return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext);
	}
}
