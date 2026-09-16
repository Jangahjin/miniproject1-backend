package com.pharmaprice.pharmacy.dto;

import java.util.List;

/** GET /api/v1/regions 응답 — 시도별로 그룹핑한 트리 구조 (docs/API.md §7). */
public record RegionGroupResponse(String sido, List<SigunguResponse> sigungus) {
}
