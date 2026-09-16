package com.pharmaprice.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.admin.dto.AdminPriceReportListItemResponse;
import com.pharmaprice.admin.dto.AdminPriceReportUpdateRequest;
import com.pharmaprice.admin.dto.AdminPriceReportUpdateResponse;
import com.pharmaprice.admin.exception.PriceReportNotFoundException;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.service.PriceStatService;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;

/** 제보 관리 — 숨김/복구/이상치 플래그 해제 (docs/API.md §8, docs/ROADMAP.md T-32). */
@Service
public class AdminReportService {

	private final PriceReportRepository priceReportRepository;
	private final PriceStatService priceStatService;

	public AdminReportService(PriceReportRepository priceReportRepository, PriceStatService priceStatService) {
		this.priceReportRepository = priceReportRepository;
		this.priceStatService = priceStatService;
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminPriceReportListItemResponse> list(
			Boolean flagged, ReportStatus status, Long pharmacyId, Long drugId, int page, int size) {
		Page<PriceReport> result = priceReportRepository.search(
				pharmacyId, drugId, null, flagged, status, PageRequest.of(page, size));
		return PageResponse.of(
				result.getContent().stream().map(AdminPriceReportListItemResponse::from).toList(),
				page, size, result.getTotalElements());
	}

	@Transactional
	public AdminPriceReportUpdateResponse update(long reportId, AdminPriceReportUpdateRequest request) {
		PriceReport report = priceReportRepository.findById(reportId)
				.orElseThrow(() -> new PriceReportNotFoundException(reportId));

		boolean wasRejected = report.getStatus() == ReportStatus.REJECTED;

		if (request.status() != null) {
			report.changeStatus(request.status());
		}
		if (request.flagged() != null) {
			report.changeFlagged(request.flagged());
		}
		// request.reason()은 감사 로그성 설명 텍스트다. price_report 스키마에 이걸 담을
		// 별도 컬럼이 없어 영속화하지 않는다 — status/flagged 자체가 상태를 표현한다.

		boolean isNowRejected = report.getStatus() == ReportStatus.REJECTED;
		if (!wasRejected && isNowRejected && report.getUser() != null) {
			report.getUser().decrementReportCount();
		}

		// PriceStatService.recalculate()는 native SQL upsert 직후 entityManager.clear()를
		// 호출해 flush되지 않은 변경을 조용히 날려버린다(T-26에서 겪은 문제와 동일 원인) —
		// 반드시 먼저 flush한다. flush()는 report뿐 아니라 report.getUser()에 방금 가한
		// 변경까지 영속성 컨텍스트 전체를 함께 내보낸다.
		priceReportRepository.saveAndFlush(report);

		PharmacyDrugPriceStat stat = priceStatService
				.recalculate(report.getPharmacy().getId(), report.getDrug().getId())
				.orElse(null);

		return AdminPriceReportUpdateResponse.of(report, stat);
	}
}
