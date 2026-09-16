package com.pharmaprice.drug.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;
import com.pharmaprice.drug.exception.DrugNotFoundException;
import com.pharmaprice.drug.repository.DrugQueryRepository;

/** 의약품 검색 · 상세 조회 (docs/API.md §3, docs/ROADMAP.md T-13). 자동완성의 백엔드다. */
@RestController
@RequestMapping("/api/v1/drugs")
public class DrugController {

	private static final int MAX_PAGE_SIZE = 50;

	private final DrugQueryRepository drugQueryRepository;

	public DrugController(DrugQueryRepository drugQueryRepository) {
		this.drugQueryRepository = drugQueryRepository;
	}

	@GetMapping
	public PageResponse<DrugSummaryResponse> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int clampedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		return drugQueryRepository.search(q, category, page, clampedSize);
	}

	@GetMapping("/{drugId}")
	public DrugDetailResponse getDetail(@PathVariable long drugId) {
		return drugQueryRepository.findDetail(drugId)
				.orElseThrow(() -> new DrugNotFoundException(drugId));
	}
}
