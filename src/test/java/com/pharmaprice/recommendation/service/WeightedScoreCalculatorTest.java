package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.recommendation.service.ScoreCalculator.Badge;
import com.pharmaprice.recommendation.service.ScoreCalculator.Candidate;
import com.pharmaprice.recommendation.service.ScoreCalculator.ScoredCandidate;

class WeightedScoreCalculatorTest {

	private static RecommendationProperties properties(double wPrice, double wDistance, double wFreshness) {
		return new RecommendationProperties(
				new RecommendationProperties.Weights(wPrice, wDistance, wFreshness),
				30, 90, 180,
				new RecommendationProperties.Outlier(1.5, 4));
	}

	private static final RecommendationProperties DEFAULT_PROPERTIES = properties(0.60, 0.25, 0.15);

	@Test
	void 후보가_1개면_0으로_나누기_없이_priceScore가_1이다() {
		WeightedScoreCalculator calculator = new WeightedScoreCalculator(DEFAULT_PROPERTIES);
		Candidate only = new Candidate(1L, 2800, 500, LocalDate.now(), 3);

		List<ScoredCandidate> result = calculator.rank(List.of(only), 2000, LocalDate.now());

		assertThat(result).hasSize(1);
		assertThat(result.get(0).breakdown().priceScore()).isEqualTo(1.0);
	}

	@Test
	void scoreBreakdown이_항상_채워진다() {
		WeightedScoreCalculator calculator = new WeightedScoreCalculator(DEFAULT_PROPERTIES);
		LocalDate today = LocalDate.now();
		List<Candidate> candidates = List.of(
				new Candidate(1L, 2800, 500, today, 3),
				new Candidate(2L, 3200, 1200, today.minusDays(10), 1));

		List<ScoredCandidate> result = calculator.rank(candidates, 2000, today);

		result.forEach(sc -> {
			assertThat(sc.breakdown()).isNotNull();
			assertThat(sc.breakdown().priceScore()).isBetween(0.0, 1.0);
			assertThat(sc.breakdown().distanceScore()).isBetween(0.0, 1.0);
			assertThat(sc.breakdown().freshnessScore()).isBetween(0.0, 1.0);
		});
	}

	@Test
	void 점수가_같으면_가격_거리_약국ID_순으로_타이브레이크한다() {
		// 가격·거리·최근 제보일이 완전히 같으면 score도 같아진다. 이 경우 유일한
		// 차이는 pharmacyId뿐이라, 정렬 안정성은 오직 그 타이브레이커에 달려 있다.
		WeightedScoreCalculator calculator = new WeightedScoreCalculator(DEFAULT_PROPERTIES);
		LocalDate today = LocalDate.now();
		List<Candidate> candidates = List.of(
				new Candidate(30L, 2800, 500, today, 3),
				new Candidate(10L, 2800, 500, today, 3),
				new Candidate(20L, 2800, 500, today, 3));

		List<ScoredCandidate> result = calculator.rank(candidates, 2000, today);

		assertThat(result).extracting(sc -> sc.candidate().pharmacyId())
				.containsExactly(10L, 20L, 30L);
	}

	@Test
	void 가중치를_바꾸면_실제_순위가_바뀐다() {
		LocalDate today = LocalDate.now();
		Candidate cheapButFar = new Candidate(1L, 2500, 1900, today, 5);
		Candidate pricierButNear = new Candidate(2L, 3000, 100, today, 5);
		List<Candidate> candidates = List.of(cheapButFar, pricierButNear);

		WeightedScoreCalculator priceFocused = new WeightedScoreCalculator(properties(0.60, 0.25, 0.15));
		WeightedScoreCalculator distanceFocused = new WeightedScoreCalculator(properties(0.10, 0.80, 0.10));

		long winnerWithDefaultWeights = priceFocused.rank(candidates, 2000, today)
				.get(0).candidate().pharmacyId();
		long winnerWithDistanceWeights = distanceFocused.rank(candidates, 2000, today)
				.get(0).candidate().pharmacyId();

		assertThat(winnerWithDefaultWeights).isEqualTo(1L); // 저가 우선
		assertThat(winnerWithDistanceWeights).isEqualTo(2L); // 거리 우선으로 뒤집힘
	}

	@Test
	void 뱃지는_최종_순위가_아니라_후보군_내_실제_최저가_최단거리_여부로_판정한다() {
		WeightedScoreCalculator calculator = new WeightedScoreCalculator(DEFAULT_PROPERTIES);
		LocalDate today = LocalDate.now();
		// cheapest는 가격은 제일 싸지만 멀고 오래된 제보라 종합 순위 1위가 아닐 수 있다.
		Candidate cheapest = new Candidate(1L, 2000, 1900, today.minusDays(40), 1);
		Candidate nearestAndFreshest = new Candidate(2L, 3500, 50, today, 5);

		List<ScoredCandidate> result = calculator.rank(List.of(cheapest, nearestAndFreshest), 2000, today);

		ScoredCandidate cheapestResult = result.stream()
				.filter(sc -> sc.candidate().pharmacyId() == 1L).findFirst().orElseThrow();
		ScoredCandidate nearestResult = result.stream()
				.filter(sc -> sc.candidate().pharmacyId() == 2L).findFirst().orElseThrow();

		assertThat(cheapestResult.badges()).contains(Badge.LOWEST_PRICE, Badge.LOW_CONFIDENCE, Badge.STALE_DATA);
		assertThat(nearestResult.badges()).contains(Badge.NEAREST);
		assertThat(nearestResult.badges()).doesNotContain(Badge.LOWEST_PRICE, Badge.STALE_DATA, Badge.LOW_CONFIDENCE);
	}
}
