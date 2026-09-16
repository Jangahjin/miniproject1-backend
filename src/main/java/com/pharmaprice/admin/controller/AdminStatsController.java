package com.pharmaprice.admin.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.admin.dto.AdminOverviewResponse;
import com.pharmaprice.admin.dto.DrugStatsResponse;
import com.pharmaprice.admin.dto.PriceGapsResponse;
import com.pharmaprice.admin.dto.RegionStatsResponse;
import com.pharmaprice.admin.repository.AdminStatsRepository;
import com.pharmaprice.drug.exception.DrugNotFoundException;

/**
 * 관리자 통계 4종 (docs/API.md §8, docs/ROADMAP.md T-31). SecurityConfig의
 * {@code /api/v1/admin/**} → hasRole(ADMIN) URL 매칭에 더해, 스펙이 명시적으로
 * 요구한 대로 메서드에도 @PreAuthorize를 붙여 의도를 이중으로 드러낸다.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

	private static final int DEFAULT_PRICE_GAPS_LIMIT = 10;
	private static final int MAX_PRICE_GAPS_LIMIT = 50;

	private final AdminStatsRepository adminStatsRepository;

	public AdminStatsController(AdminStatsRepository adminStatsRepository) {
		this.adminStatsRepository = adminStatsRepository;
	}

	@GetMapping("/overview")
	@PreAuthorize("hasRole('ADMIN')")
	public AdminOverviewResponse overview() {
		return adminStatsRepository.overview();
	}

	@GetMapping("/regions")
	@PreAuthorize("hasRole('ADMIN')")
	public RegionStatsResponse regions(
			@RequestParam(required = false) String regionCode,
			@RequestParam(required = false) Long drugId,
			@RequestParam(required = false) String sido) {
		return adminStatsRepository.regions(regionCode, drugId, sido);
	}

	@GetMapping("/drugs/{drugId}")
	@PreAuthorize("hasRole('ADMIN')")
	public DrugStatsResponse drugStats(@PathVariable long drugId) {
		return adminStatsRepository.drugStats(drugId).orElseThrow(() -> new DrugNotFoundException(drugId));
	}

	@GetMapping("/price-gaps")
	@PreAuthorize("hasRole('ADMIN')")
	public PriceGapsResponse priceGaps(@RequestParam(defaultValue = "" + DEFAULT_PRICE_GAPS_LIMIT) int limit) {
		int clampedLimit = Math.clamp(limit, 1, MAX_PRICE_GAPS_LIMIT);
		return adminStatsRepository.priceGaps(clampedLimit);
	}
}
