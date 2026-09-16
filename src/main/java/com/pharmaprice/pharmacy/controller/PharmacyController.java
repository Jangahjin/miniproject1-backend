package com.pharmaprice.pharmacy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;
import com.pharmaprice.pharmacy.exception.PharmacyNotFoundException;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository;
import com.pharmaprice.recommendation.distance.HaversineDistanceCalculator;

/** 약국 검색 · 상세 조회 (docs/API.md §4, docs/ROADMAP.md T-19). 가격 제보 폼의 약국 선택용이기도 하다. */
@RestController
@RequestMapping("/api/v1/pharmacies")
public class PharmacyController {

	private static final int MAX_RADIUS_M = 10_000;
	private static final int MAX_PAGE_SIZE = 50;
	private static final int DEFAULT_HISTORY_DAYS = 180;
	private static final int MAX_HISTORY_DAYS = 365;

	private final PharmacyQueryRepository pharmacyQueryRepository;

	public PharmacyController(PharmacyQueryRepository pharmacyQueryRepository) {
		this.pharmacyQueryRepository = pharmacyQueryRepository;
	}

	@GetMapping
	public PageResponse<PharmacySummaryResponse> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng,
			@RequestParam(defaultValue = "2000") int radius,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		boolean hasQuery = StringUtils.hasText(q);
		boolean hasLocation = lat != null && lng != null;
		if (!hasQuery && !hasLocation) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "q 또는 lat/lng 중 하나는 필수입니다.");
		}
		if (hasLocation) {
			validateCoordinate(lat, lng);
		}

		int clampedRadius = Math.clamp(radius, 1, MAX_RADIUS_M);
		int clampedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		return pharmacyQueryRepository.search(hasQuery ? q : null, lat, lng, clampedRadius, page, clampedSize);
	}

	@GetMapping("/{pharmacyId}")
	public PharmacyDetailResponse getDetail(
			@PathVariable long pharmacyId,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng) {
		if (lat != null && lng != null) {
			validateCoordinate(lat, lng);
		}
		return pharmacyQueryRepository.findDetail(pharmacyId, lat, lng)
				.orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));
	}

	// docs/ROADMAP.md T-20. flagged=true 인 제보도 포함해 그대로 내려준다 — 이상치
	// 처리를 프론트에 드러내는 유일한 지점이라 서버에서 걸러내면 안 된다.
	@GetMapping("/{pharmacyId}/drugs/{drugId}/history")
	public PriceHistoryResponse getHistory(
			@PathVariable long pharmacyId,
			@PathVariable long drugId,
			@RequestParam(defaultValue = "" + DEFAULT_HISTORY_DAYS) int days) {
		int clampedDays = Math.clamp(days, 1, MAX_HISTORY_DAYS);
		return pharmacyQueryRepository.findPriceHistory(pharmacyId, drugId, clampedDays);
	}

	private static void validateCoordinate(double lat, double lng) {
		try {
			HaversineDistanceCalculator.validateCoordinate(lat, lng);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
}
