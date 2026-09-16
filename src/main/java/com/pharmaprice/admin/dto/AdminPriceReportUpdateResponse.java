package com.pharmaprice.admin.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;

/** PATCH /api/v1/admin/price-reports/{reportId} 응답 (docs/API.md §8). */
public record AdminPriceReportUpdateResponse(
		long id, ReportStatus status, boolean flagged, OffsetDateTime updatedAt, RecalculatedStat recalculatedStat) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 유효 제보가 0건이 되면 통계 자체가 삭제되어 null일 수 있다. */
	public record RecalculatedStat(long pharmacyId, long drugId, int repPrice, int reportCount) {
	}

	public static AdminPriceReportUpdateResponse of(PriceReport report, PharmacyDrugPriceStat stat) {
		RecalculatedStat recalculated = stat != null
				? new RecalculatedStat(
						report.getPharmacy().getId(), report.getDrug().getId(), stat.getRepPrice(), stat.getReportCount())
				: null;
		return new AdminPriceReportUpdateResponse(
				report.getId(), report.getStatus(), report.isFlagged(),
				report.getUpdatedAt().atZone(KST).toOffsetDateTime(), recalculated);
	}
}
