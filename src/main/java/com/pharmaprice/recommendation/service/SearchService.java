package com.pharmaprice.recommendation.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.exception.DrugNotFoundException;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.controller.SortOption;
import com.pharmaprice.recommendation.distance.DistanceCalculator;
import com.pharmaprice.recommendation.distance.DistanceCalculator.BoundingBox;
import com.pharmaprice.recommendation.dto.SearchResponse;
import com.pharmaprice.recommendation.repository.SearchQueryRepository;
import com.pharmaprice.recommendation.repository.SearchQueryRepository.CandidateRow;
import com.pharmaprice.recommendation.service.ScoreCalculator.Candidate;
import com.pharmaprice.recommendation.service.ScoreCalculator.ScoredCandidate;

@Service
public class SearchService {

	private static final Logger log = LoggerFactory.getLogger(SearchService.class);

	// HaversineDistanceCalculator의 허용 반경과 동일하다. 다음 단계 반경 제안(F3-9)에만 쓰인다.
	private static final List<Integer> ALLOWED_RADII = List.of(500, 1000, 2000, 5000);

	private final DrugRepository drugRepository;
	private final RegionRepository regionRepository;
	private final DistanceCalculator distanceCalculator;
	private final ScoreCalculator scoreCalculator;
	private final SearchQueryRepository searchQueryRepository;
	private final RecommendationProperties properties;

	public SearchService(
			DrugRepository drugRepository,
			RegionRepository regionRepository,
			DistanceCalculator distanceCalculator,
			ScoreCalculator scoreCalculator,
			SearchQueryRepository searchQueryRepository,
			RecommendationProperties properties) {
		this.drugRepository = drugRepository;
		this.regionRepository = regionRepository;
		this.distanceCalculator = distanceCalculator;
		this.scoreCalculator = scoreCalculator;
		this.searchQueryRepository = searchQueryRepository;
		this.properties = properties;
	}

	public SearchResponse search(
			long drugId, Double lat, Double lng, String regionCode, int radius, SortOption sort, int limit) {
		Drug drug = drugRepository.findById(drugId)
				.filter(Drug::isOtcFlag)
				.orElseThrow(() -> new DrugNotFoundException(drugId));

		Location location = resolveLocation(lat, lng, regionCode);

		BoundingBox box;
		try {
			box = distanceCalculator.boundingBox(location.lat(), location.lng(), radius);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}

		List<CandidateRow> rows = searchQueryRepository.findCandidates(
				drugId, location.lat(), location.lng(), box, radius);

		SearchResponse.QueryEcho query = new SearchResponse.QueryEcho(
				location.lat(), location.lng(), radius, sort.name(), location.source());

		if (rows.isEmpty()) {
			SearchResponse.Suggestion suggestion = buildSuggestion(drugId, location, radius);
			return new SearchResponse(
					toDrugSummary(drug), query,
					new SearchResponse.Summary(0, null, null, null, null),
					"SEED", List.of(), suggestion);
		}

		List<Candidate> candidates = rows.stream()
				.map(r -> new Candidate(r.pharmacyId(), r.repPrice(), r.distanceM(), r.lastReportedAt(), r.reportCount()))
				.toList();

		LocalDate today = LocalDate.now();
		List<ScoredCandidate> scoreRanked = scoreCalculator.rank(candidates, radius, today);
		log.debug("검색 Score 순위 drugId={} radius={} sort={} ranked={}", drugId, radius, sort, scoreRanked);

		long recommendedPharmacyId = scoreRanked.get(0).candidate().pharmacyId();
		List<ScoredCandidate> displayOrder = applySort(scoreRanked, sort);
		List<ScoredCandidate> limited = displayOrder.stream().limit(limit).toList();

		IntSummaryStatistics priceStats = candidates.stream()
				.mapToInt(Candidate::repPrice).summaryStatistics();
		int avgPrice = (int) Math.round(priceStats.getAverage());
		int minPrice = priceStats.getMin();
		int maxPrice = priceStats.getMax();

		Map<Long, CandidateRow> rowsByPharmacyId = rows.stream()
				.collect(Collectors.toMap(CandidateRow::pharmacyId, r -> r));

		List<SearchResponse.ResultItem> results = new ArrayList<>();
		for (int i = 0; i < limited.size(); i++) {
			ScoredCandidate sc = limited.get(i);
			CandidateRow row = rowsByPharmacyId.get(sc.candidate().pharmacyId());
			results.add(toResultItem(i + 1, sc, row, avgPrice, recommendedPharmacyId, today));
		}

		List<Long> resultPharmacyIds = limited.stream().map(sc -> sc.candidate().pharmacyId()).toList();
		String dataSource = resolveDataSource(drugId, resultPharmacyIds);

		return new SearchResponse(
				toDrugSummary(drug), query,
				new SearchResponse.Summary(scoreRanked.size(), avgPrice, minPrice, maxPrice, maxPrice - minPrice),
				dataSource, results, null);
	}

	private Location resolveLocation(Double lat, Double lng, String regionCode) {
		if (lat != null && lng != null) {
			return new Location(lat, lng, "GPS");
		}
		if (regionCode != null && !regionCode.isBlank()) {
			Region region = regionRepository.findById(regionCode)
					.orElseThrow(() -> new ResponseStatusException(
							HttpStatus.BAD_REQUEST, "존재하지 않는 지역 코드입니다: " + regionCode));
			return new Location(region.getCenterLat(), region.getCenterLng(), "REGION");
		}
		throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST, "lat/lng 조합 또는 regionCode 중 하나는 필수입니다.");
	}

	private List<ScoredCandidate> applySort(List<ScoredCandidate> scoreRanked, SortOption sort) {
		Comparator<ScoredCandidate> comparator = switch (sort) {
			case SCORE -> null; // rank()가 이미 score DESC 기준으로 정렬해뒀다.
			case PRICE -> Comparator.<ScoredCandidate>comparingInt(sc -> sc.candidate().repPrice())
					.thenComparingDouble(sc -> sc.candidate().distanceM())
					.thenComparingLong(sc -> sc.candidate().pharmacyId());
			case DISTANCE -> Comparator.<ScoredCandidate>comparingDouble(sc -> sc.candidate().distanceM())
					.thenComparingInt(sc -> sc.candidate().repPrice())
					.thenComparingLong(sc -> sc.candidate().pharmacyId());
		};
		return comparator == null ? scoreRanked : scoreRanked.stream().sorted(comparator).toList();
	}

	private SearchResponse.ResultItem toResultItem(
			int rank, ScoredCandidate sc, CandidateRow row, int candidateAvgPrice,
			long recommendedPharmacyId, LocalDate today) {
		Candidate c = sc.candidate();
		long daysSinceLastReport = ChronoUnit.DAYS.between(c.lastReportedAt(), today);
		RecommendationProperties.Weights w = properties.weights();

		return new SearchResponse.ResultItem(
				rank,
				c.pharmacyId() == recommendedPharmacyId,
				new SearchResponse.PharmacySummary(row.pharmacyId(), row.name(), row.addressRoad(), row.lat(), row.lng(), row.phone()),
				new SearchResponse.PriceInfo(
						c.repPrice(), row.minPrice(), row.avgPrice(),
						candidateAvgPrice - c.repPrice(),
						c.reportCount(), c.lastReportedAt(), daysSinceLastReport),
				c.distanceM(),
				sc.score(),
				new SearchResponse.ScoreBreakdownResponse(
						sc.breakdown().priceScore(), sc.breakdown().distanceScore(), sc.breakdown().freshnessScore(),
						new SearchResponse.Weights(w.price(), w.distance(), w.freshness())),
				sc.badges().stream().map(Enum::name).toList());
	}

	private String resolveDataSource(long drugId, List<Long> pharmacyIds) {
		List<String> sources = searchQueryRepository.findDistinctSources(drugId, pharmacyIds);
		if (sources.isEmpty()) {
			return "SEED";
		}
		boolean hasSeed = sources.contains("SEED");
		boolean hasNonSeed = sources.stream().anyMatch(s -> !s.equals("SEED"));
		if (hasSeed && hasNonSeed) {
			return "MIXED";
		}
		return hasSeed ? "SEED" : "USER";
	}

	/** 반경을 한 단계 넓혔을 때의 예상 건수를 실제로 계산해서 내려준다 (docs/PRD.md F3-9). */
	private SearchResponse.Suggestion buildSuggestion(long drugId, Location location, int radius) {
		int currentIndex = ALLOWED_RADII.indexOf(radius);
		if (currentIndex < 0 || currentIndex == ALLOWED_RADII.size() - 1) {
			return null; // 이미 최대 반경이면 더 확대할 수 없다.
		}
		int nextRadius = ALLOWED_RADII.get(currentIndex + 1);
		BoundingBox biggerBox = distanceCalculator.boundingBox(location.lat(), location.lng(), nextRadius);
		long estimatedCount = searchQueryRepository
				.findCandidates(drugId, location.lat(), location.lng(), biggerBox, nextRadius)
				.size();
		return new SearchResponse.Suggestion("EXPAND_RADIUS", nextRadius, estimatedCount);
	}

	private SearchResponse.DrugSummary toDrugSummary(Drug drug) {
		return new SearchResponse.DrugSummary(drug.getId(), drug.getDisplayName(), drug.getPackageUnit(), drug.getImageUrl());
	}

	private record Location(double lat, double lng, String source) {
	}
}
