package com.pharmaprice.recommendation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.recommendation.dto.SearchResponse;
import com.pharmaprice.recommendation.service.SearchService;

/** 위치 기준 최저가 추천 — 이 서비스의 핵심 엔드포인트다 (docs/API.md §5, docs/ROADMAP.md T-15). */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

	private static final int MAX_LIMIT = 50;

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping
	public SearchResponse search(
			@RequestParam long drugId,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng,
			@RequestParam(required = false) String regionCode,
			@RequestParam(defaultValue = "2000") int radius,
			@RequestParam(defaultValue = "SCORE") SortOption sort,
			@RequestParam(defaultValue = "20") int limit) {
		int clampedLimit = Math.clamp(limit, 1, MAX_LIMIT);
		return searchService.search(drugId, lat, lng, regionCode, radius, sort, clampedLimit);
	}
}
