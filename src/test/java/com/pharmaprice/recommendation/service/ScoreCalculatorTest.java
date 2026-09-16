package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.recommendation.service.ScoreCalculator.Badge;
import com.pharmaprice.recommendation.service.ScoreCalculator.Candidate;
import com.pharmaprice.recommendation.service.ScoreCalculator.ScoredCandidate;

/**
 * docs/ROADMAP.md T-12 "ScoreCalculator 테스트" 8종. 절대 점수값이 아니라
 * 상대 순위를 검증해, application.yml의 가중치가 바뀌어도 테스트 의미가
 * 유지되게 한다 (단, 7번은 실제 운영 가중치로 재현되는 반전 사례라 예외적으로
 * 그 가중치를 명시해서 검증한다).
 */
class ScoreCalculatorTest {

	// application.yml의 실제 운영 값과 동일 (docs/API.md §5).
	private static final RecommendationProperties PRODUCTION_PROPERTIES = new RecommendationProperties(
			new RecommendationProperties.Weights(0.60, 0.25, 0.15),
			30, 90, 180,
			new RecommendationProperties.Outlier(1.5, 4));

	private final WeightedScoreCalculator calculator = new WeightedScoreCalculator(PRODUCTION_PROPERTIES);

	@Test
	void 거리와_신선도가_같으면_가격이_싼_쪽이_상위() {
		LocalDate today = LocalDate.now();
		Candidate cheaper = new Candidate(1L, 2500, 1000, today, 3);
		Candidate pricier = new Candidate(2L, 3000, 1000, today, 3);

		List<ScoredCandidate> result = calculator.rank(List.of(pricier, cheaper), 2000, today);

		assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(cheaper.pharmacyId());
	}

	@Test
	void 가격과_신선도가_같으면_거리가_가까운_쪽이_상위() {
		LocalDate today = LocalDate.now();
		Candidate nearer = new Candidate(1L, 2800, 200, today, 3);
		Candidate farther = new Candidate(2L, 2800, 1800, today, 3);

		List<ScoredCandidate> result = calculator.rank(List.of(farther, nearer), 2000, today);

		assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(nearer.pharmacyId());
	}

	@Test
	void 가격과_거리가_같으면_최근_제보한_쪽이_상위() {
		LocalDate today = LocalDate.now();
		Candidate fresher = new Candidate(1L, 2800, 500, today, 3);
		Candidate staler = new Candidate(2L, 2800, 500, today.minusDays(60), 3);

		List<ScoredCandidate> result = calculator.rank(List.of(staler, fresher), 2000, today);

		assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(fresher.pharmacyId());
	}

	@Test
	void 후보가_1개면_priceScore가_1이고_예외가_없다() {
		LocalDate today = LocalDate.now();
		Candidate only = new Candidate(1L, 2800, 500, today, 3);

		List<ScoredCandidate> result = calculator.rank(List.of(only), 2000, today);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).breakdown().priceScore()).isEqualTo(1.0);
	}

	@Test
	void 모든_후보_가격이_같으면_전원_priceScore가_1이다() {
		LocalDate today = LocalDate.now();
		List<Candidate> candidates = List.of(
				new Candidate(1L, 2800, 300, today, 3),
				new Candidate(2L, 2800, 900, today, 3),
				new Candidate(3L, 2800, 1500, today, 3));

		List<ScoredCandidate> result = calculator.rank(candidates, 2000, today);

		assertThat(result).allSatisfy(sc -> assertThat(sc.breakdown().priceScore()).isEqualTo(1.0));
	}

	@Test
	void 동일_입력을_두번_호출해도_순서가_완전히_일치한다() {
		LocalDate today = LocalDate.now();
		List<Candidate> candidates = List.of(
				new Candidate(3L, 2900, 1500, today.minusDays(5), 2),
				new Candidate(1L, 2500, 400, today, 4),
				new Candidate(2L, 2500, 400, today, 1));

		List<Long> firstOrder = pharmacyIdOrder(calculator.rank(candidates, 2000, today));
		List<Long> secondOrder = pharmacyIdOrder(calculator.rank(candidates, 2000, today));

		assertThat(secondOrder).containsExactlyElementsOf(firstOrder);
	}

	@Test
	void 최저가가_반경_경계에_있고_근접_2위가_코앞이면_거리_가중치로_순위가_역전된다() {
		// 실제 운영 가중치(price 0.60 / distance 0.25)로도 재현되는 사례다. 최저가 A(2500원)와
		// 가격이 근접한 B(2550원)가 있을 때, A는 반경 경계(1950m)에, B는 바로 앞(50m)에 있으면
		// distanceScore 차이(0.95)가 가중치 0.25를 곱해도 priceScore 차이(단 0.0333)에 가중치
		// 0.60을 곱한 것보다 커져 B가 역전한다. 가격 폭을 넓히는 C가 있어야 A·B의 priceScore
		// 차이가 작아진다(후보가 A·B 둘뿐이면 최저가는 항상 priceScore=1.0, 다른 쪽은 항상
		// 0.0이 되어 가격 우위가 절대 뒤집히지 않는다).
		LocalDate today = LocalDate.now();
		Candidate cheapestAtBoundary = new Candidate(1L, 2500, 1950, today, 5);
		Candidate secondPlaceNearby = new Candidate(2L, 2550, 50, today, 5);
		Candidate priceRangeAnchor = new Candidate(3L, 4000, 1000, today, 5);

		List<ScoredCandidate> result = calculator.rank(
				List.of(cheapestAtBoundary, secondPlaceNearby, priceRangeAnchor), 2000, today);

		assertThat(result.get(0).candidate().pharmacyId()).isEqualTo(secondPlaceNearby.pharmacyId());
		assertThat(result.get(1).candidate().pharmacyId()).isEqualTo(cheapestAtBoundary.pharmacyId());
	}

	@Test
	void 제보가_1건뿐이고_40일_지났으면_LOW_CONFIDENCE와_STALE_DATA_뱃지가_붙는다() {
		LocalDate today = LocalDate.now();
		Candidate lowConfidenceAndStale = new Candidate(1L, 2800, 500, today.minusDays(40), 1);

		List<ScoredCandidate> result = calculator.rank(List.of(lowConfidenceAndStale), 2000, today);

		assertThat(result.get(0).badges()).contains(Badge.LOW_CONFIDENCE, Badge.STALE_DATA);
	}

	private static List<Long> pharmacyIdOrder(List<ScoredCandidate> scored) {
		return scored.stream().map(sc -> sc.candidate().pharmacyId()).toList();
	}
}
