package com.pharmaprice.pharmacy.dto;

/** 약국 응답에 포함되는 지역 요약 (docs/API.md §4). */
public record RegionRefResponse(String code, String sido, String sigungu) {
}
