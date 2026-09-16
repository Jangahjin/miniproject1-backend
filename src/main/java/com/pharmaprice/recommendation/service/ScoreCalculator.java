package com.pharmaprice.recommendation.service;

import java.time.LocalDate;
import java.util.List;

import com.pharmaprice.recommendation.dto.ScoreBreakdown;

/**
 * 가격·거리·신선도를 가중 합산해 검색 후보의 순위를 산출한다 (docs/API.md §5).
 * DB 접근 없는 순수 함수로 둬 단위 테스트를 쉽게 만든다 (docs/ROADMAP.md T-11).
 */
public interface ScoreCalculator {

	List<ScoredCandidate> rank(List<Candidate> candidates, int radiusM, LocalDate today);

	record Candidate(
			long pharmacyId, int repPrice, double distanceM,
			LocalDate lastReportedAt, int reportCount) {
	}

	record ScoredCandidate(Candidate candidate, double score, ScoreBreakdown breakdown, List<Badge> badges) {
	}

	enum Badge {
		LOWEST_PRICE, LOW_CONFIDENCE, STALE_DATA, NEAREST
	}
}
