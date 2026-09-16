package com.pharmaprice.recommendation.dto;

/** 순위 근거를 설명하기 위해 항상 함께 반환하는 요인별 점수 (소수점 4자리로 반올림됨). */
public record ScoreBreakdown(double priceScore, double distanceScore, double freshnessScore) {
}
