package com.pharmaprice.report.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.report.dto.PriceReportRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.service.PriceReportService;

import jakarta.validation.Valid;

/** 가격 제보 생성 (docs/API.md §6, docs/ROADMAP.md T-26). */
@RestController
@RequestMapping("/api/v1/price-reports")
public class PriceReportController {

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
}
