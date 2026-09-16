package com.pharmaprice.recommendation.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.recommendation.dto.ScoreBreakdown;
import com.pharmaprice.recommendation.service.ScoreCalculator.Badge;
import com.pharmaprice.recommendation.service.ScoreCalculator.Candidate;
import com.pharmaprice.recommendation.service.ScoreCalculator.ScoredCandidate;

@Component
public class WeightedScoreCalculator implements ScoreCalculator {

	// STALE_DATA 뱃지 기준일. freshnessHalfLifeDays(반감기, Score 공식에 쓰임)와는
	// 별개의 UI 표시 규칙이라 설정값으로 빼지 않는다 (docs/ROADMAP.md T-11 가이드 7번).
	private static final int STALE_DATA_THRESHOLD_DAYS = 30;

	private static final Comparator<ScoredCandidate> RANKING_ORDER = Comparator
			.comparingDouble(ScoredCandidate::score).reversed()
			.thenComparingInt(sc -> sc.candidate().repPrice())
			.thenComparingDouble(sc -> sc.candidate().distanceM())
			.thenComparingLong(sc -> sc.candidate().pharmacyId());

	private final RecommendationProperties properties;

	public WeightedScoreCalculator(RecommendationProperties properties) {
		this.properties = properties;
	}

	@Override
	public List<ScoredCandidate> rank(List<Candidate> candidates, int radiusM, LocalDate today) {
		if (candidates.isEmpty()) {
			return List.of();
		}

		int priceMin = candidates.stream().mapToInt(Candidate::repPrice).min().orElseThrow();
		int priceMax = candidates.stream().mapToInt(Candidate::repPrice).max().orElseThrow();
		double nearestDistanceM = candidates.stream().mapToDouble(Candidate::distanceM).min().orElseThrow();

		List<ScoredCandidate> scored = candidates.stream()
				.map(c -> score(c, priceMin, priceMax, radiusM, today))
				.sorted(RANKING_ORDER)
				.toList();

		scored.forEach(sc -> applyBadges(sc, priceMin, nearestDistanceM, today));
		return scored;
	}

	private ScoredCandidate score(Candidate c, int priceMin, int priceMax, int radiusM, LocalDate today) {
		RecommendationProperties.Weights weights = properties.weights();

		double priceScore = (priceMax == priceMin)
				? 1.0
				: round4((double) (priceMax - c.repPrice()) / (priceMax - priceMin));
		double distanceScore = round4(clamp(1 - c.distanceM() / radiusM, 0, 1));
		long ageDays = ChronoUnit.DAYS.between(c.lastReportedAt(), today);
		double freshnessScore = round4(Math.pow(0.5, (double) ageDays / properties.freshnessHalfLifeDays()));

		double score = round4(
				weights.price() * priceScore
						+ weights.distance() * distanceScore
						+ weights.freshness() * freshnessScore);

		return new ScoredCandidate(c, score, new ScoreBreakdown(priceScore, distanceScore, freshnessScore),
				new ArrayList<>());
	}

	/**
	 * LOWEST_PRICE/NEAREST는 최종 순위(score 기준)가 아니라 후보군 내 실제 최저가·최단거리
	 * 여부로 판정한다. 거리 가중치 때문에 최저가 약국이 1위가 아닐 수 있는데(T-12 테스트 7번),
	 * 그 경우에도 "이 약국이 진짜 최저가다"를 사용자에게 보여줘야 하기 때문이다.
	 */
	private void applyBadges(ScoredCandidate sc, int priceMin, double nearestDistanceM, LocalDate today) {
		Candidate c = sc.candidate();
		List<Badge> badges = sc.badges();

		if (c.repPrice() == priceMin) {
			badges.add(Badge.LOWEST_PRICE);
		}
		if (c.reportCount() == 1) {
			badges.add(Badge.LOW_CONFIDENCE);
		}
		if (ChronoUnit.DAYS.between(c.lastReportedAt(), today) > STALE_DATA_THRESHOLD_DAYS) {
			badges.add(Badge.STALE_DATA);
		}
		if (c.distanceM() == nearestDistanceM) {
			badges.add(Badge.NEAREST);
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double round4(double value) {
		return Math.round(value * 10_000.0) / 10_000.0;
	}
}
