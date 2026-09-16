package com.pharmaprice.admin.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;

/**
 * GET /api/v1/admin/price-reports 목록 항목 (docs/API.md §8). 일반 목록(T-28)과
 * 달리 reporter에 id·email, 그리고 flagReason·receiptFileId까지 노출한다 —
 * 관리자 전용이라 개인정보 노출 제약이 적용되지 않는다.
 */
public record AdminPriceReportListItemResponse(
		long id, PharmacyRef pharmacy, DrugRef drug, int price, LocalDate purchasedAt,
		ReporterRef reporter, ReportSource source, ReportStatus status, boolean flagged,
		FlagReason flagReason, Long receiptFileId, OffsetDateTime createdAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public record PharmacyRef(long id, String name) {
	}

	public record DrugRef(long id, String displayName, String packageUnit) {
	}

	public record ReporterRef(long id, String nickname, String email) {
	}

	public static AdminPriceReportListItemResponse from(PriceReport report) {
		return new AdminPriceReportListItemResponse(
				report.getId(),
				new PharmacyRef(report.getPharmacy().getId(), report.getPharmacy().getName()),
				new DrugRef(
						report.getDrug().getId(), report.getDrug().getDisplayName(),
						report.getDrug().getPackageUnit()),
				report.getPrice(), report.getPurchasedAt(),
				report.getUser() != null
						? new ReporterRef(report.getUser().getId(), report.getUser().getNickname(), report.getUser().getEmail())
						: null,
				report.getSource(), report.getStatus(), report.isFlagged(), report.getFlagReason(),
				report.getReceiptFile() != null ? report.getReceiptFile().getId() : null,
				report.getCreatedAt().atZone(KST).toOffsetDateTime());
	}
}
