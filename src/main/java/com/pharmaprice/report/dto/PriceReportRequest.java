package com.pharmaprice.report.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/v1/price-reports 요청 (docs/API.md §6). */
public record PriceReportRequest(
		@NotNull(message = "{report.pharmacyId.required}") Long pharmacyId,
		@NotNull(message = "{report.drugId.required}") Long drugId,
		@NotNull(message = "{report.price.required}")
		@Min(value = 100, message = "{report.price.range}")
		@Max(value = 200_000, message = "{report.price.range}")
		Integer price,
		LocalDate purchasedAt,
		Long receiptFileId,
		@Size(max = 200, message = "{report.memo.size}") String memo) {
}
