package com.pharmaprice.admin.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.admin.dto.AdminPriceReportListItemResponse;
import com.pharmaprice.admin.dto.AdminPriceReportUpdateRequest;
import com.pharmaprice.admin.dto.AdminPriceReportUpdateResponse;
import com.pharmaprice.admin.service.AdminReportService;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.report.domain.ReportStatus;

/** 제보 관리 (docs/API.md §8, docs/ROADMAP.md T-32). */
@RestController
@RequestMapping("/api/v1/admin/price-reports")
public class AdminReportController {

	private static final int MAX_PAGE_SIZE = 50;

	private final AdminReportService adminReportService;

	public AdminReportController(AdminReportService adminReportService) {
		this.adminReportService = adminReportService;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public PageResponse<AdminPriceReportListItemResponse> list(
			@RequestParam(required = false) Boolean flagged,
			@RequestParam(required = false) ReportStatus status,
			@RequestParam(required = false) Long pharmacyId,
			@RequestParam(required = false) Long drugId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int clampedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		return adminReportService.list(flagged, status, pharmacyId, drugId, page, clampedSize);
	}

	@PatchMapping("/{reportId}")
	@PreAuthorize("hasRole('ADMIN')")
	public AdminPriceReportUpdateResponse update(
			@PathVariable long reportId, @RequestBody AdminPriceReportUpdateRequest request) {
		return adminReportService.update(reportId, request);
	}
}
