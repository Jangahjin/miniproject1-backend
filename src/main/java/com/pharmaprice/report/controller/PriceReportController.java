package com.pharmaprice.report.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.report.dto.PriceReportListItemResponse;
import com.pharmaprice.report.dto.PriceReportRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.service.PriceReportService;

import jakarta.validation.Valid;

/** 가격 제보 생성·목록 조회 (docs/API.md §6, docs/ROADMAP.md T-26, T-28, T-29). */
@RestController
@RequestMapping("/api/v1/price-reports")
public class PriceReportController {

	private static final int MAX_PAGE_SIZE = 50;

	private final PriceReportService priceReportService;

	public PriceReportController(PriceReportService priceReportService) {
		this.priceReportService = priceReportService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PriceReportResponse create(
			@AuthenticationPrincipal Long userId, @Valid @RequestBody PriceReportRequest request) {
		return priceReportService.create(userId, request);
	}

	@GetMapping
	public PageResponse<PriceReportListItemResponse> list(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) Long pharmacyId,
			@RequestParam(required = false) Long drugId,
			@RequestParam(defaultValue = "false") boolean mine,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (mine && userId == null) {
			// permitAll 경로라 AuthorizationFilter는 이 요청을 막지 않는다 — 여기서 직접
			// AuthenticationException을 던져야 ExceptionTranslationFilter가 잡아 T-23의
			// JwtAuthenticationEntryPoint로 넘긴다.
			throw new InsufficientAuthenticationException("본인 제보 조회는 로그인이 필요합니다.");
		}
		int clampedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		return priceReportService.list(pharmacyId, drugId, mine ? userId : null, page, clampedSize);
	}
}
