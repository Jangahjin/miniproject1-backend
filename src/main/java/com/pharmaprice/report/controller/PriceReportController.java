package com.pharmaprice.report.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.common.dto.ErrorResponse;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.report.dto.PriceReportListItemResponse;
import com.pharmaprice.report.dto.PriceReportRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.exception.DuplicateReportException;
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

	// 전역 예외 처리(T-35)가 붙기 전까지 이 컨트롤러만 국소적으로 API.md §1.2 포맷을
	// 맞춘다. 프론트(app/reports/new/page.tsx)가 error.code === "DUPLICATE_REPORT"로
	// 분기하는데, 이게 없으면 Spring 기본 에러 바디(code 필드 없음)로 나가 분기를 못
	// 타고 예외의 원본 메시지(pharmacyId=... 같은 내부 값 포함)가 그대로 화면에 노출된다.
	@ExceptionHandler(DuplicateReportException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse handleDuplicateReport() {
		return ErrorResponse.of("DUPLICATE_REPORT", "오늘 이미 이 약국의 해당 약품 가격을 제보하셨습니다.");
	}
}
