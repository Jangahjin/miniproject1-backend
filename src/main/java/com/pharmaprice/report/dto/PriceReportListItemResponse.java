package com.pharmaprice.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;

/**
 * GET /api/v1/price-reports 목록 항목 (docs/API.md §6, docs/ROADMAP.md T-28).
 * reporter는 닉네임만, 영수증은 hasReceipt(boolean)만 노출한다 — id·이메일·파일 id를
 * 일반 사용자에게 내려주지 않는다.
 */
public record PriceReportListItemResponse(
		long id, PharmacyRef pharmacy, DrugRef drug, int price, LocalDate purchasedAt,
		ReporterRef reporter, ReportSource source, ReportStatus status, boolean flagged,
		boolean hasReceipt, OffsetDateTime createdAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public record PharmacyRef(long id, String name) {
	}

	public record DrugRef(long id, String displayName, String packageUnit) {
	}

	public record ReporterRef(String nickname) {
	}

	public static PriceReportListItemResponse from(PriceReport report) {
		return new PriceReportListItemResponse(
				report.getId(),
				new PharmacyRef(report.getPharmacy().getId(), report.getPharmacy().getName()),
				new DrugRef(
						report.getDrug().getId(), report.getDrug().getDisplayName(),
						report.getDrug().getPackageUnit()),
				report.getPrice(), report.getPurchasedAt(),
				report.getUser() != null ? new ReporterRef(report.getUser().getNickname()) : null,
				report.getSource(), report.getStatus(), report.isFlagged(),
				report.getReceiptFile() != null, report.getCreatedAt().atZone(KST).toOffsetDateTime());
	}
}
