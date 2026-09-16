package com.pharmaprice.admin.dto;

import com.pharmaprice.report.domain.ReportStatus;

/**
 * PATCH /api/v1/admin/price-reports/{reportId} 요청 (docs/API.md §8).
 * 세 필드 모두 선택이며, 넘어온 필드만 변경한다.
 */
public record AdminPriceReportUpdateRequest(ReportStatus status, Boolean flagged, String reason) {
}
