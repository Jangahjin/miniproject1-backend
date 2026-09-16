package com.pharmaprice.report.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/v1/price-reports 요청 (docs/API.md §6). */
public record PriceReportRequest(
		@NotNull Long pharmacyId,
		@NotNull Long drugId,
		@NotNull @Min(100) @Max(200_000) Integer price,
		LocalDate purchasedAt,
		Long receiptFileId,
		@Size(max = 200) String memo) {
}
