package com.pharmaprice.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;

/** POST /api/v1/price-reports 응답 (docs/API.md §6). */
public record PriceReportResponse(
		long id, long pharmacyId, long drugId, int price, LocalDate purchasedAt,
		ReportStatus status, boolean flagged, FlagReason flagReason,
		OffsetDateTime createdAt, String warning, UpdatedStat updatedStat) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static PriceReportResponse of(PriceReport report, PharmacyDrugPriceStat stat, String warning) {
		return new PriceReportResponse(
				report.getId(), report.getPharmacy().getId(), report.getDrug().getId(), report.getPrice(),
				report.getPurchasedAt(), report.getStatus(), report.isFlagged(), report.getFlagReason(),
				report.getCreatedAt().atZone(KST).toOffsetDateTime(), warning,
				stat != null ? UpdatedStat.from(stat) : null);
	}

	/** {@code recalculate()} 가 유효 제보 0건이라 통계 자체가 없을 수 있어(방금 제보가 이상치인 첫 제보인 경우) nullable이다. */
	public record UpdatedStat(int repPrice, int minPrice, int avgPrice, int reportCount, LocalDate lastReportedAt) {

		static UpdatedStat from(PharmacyDrugPriceStat stat) {
			return new UpdatedStat(
					stat.getRepPrice(), stat.getMinPrice(), stat.getAvgPrice(), stat.getReportCount(),
					stat.getLastReportedAt());
		}
	}
}
