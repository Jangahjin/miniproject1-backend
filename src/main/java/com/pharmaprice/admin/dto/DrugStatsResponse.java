package com.pharmaprice.admin.dto;

import java.util.List;

/** GET /api/v1/admin/stats/drugs/{drugId} 응답 (docs/API.md §8). */
public record DrugStatsResponse(DrugRef drug, List<Bucket> distribution, List<RegionAvg> byRegion, National national) {

	public record DrugRef(long id, String displayName, String packageUnit) {
	}

	/** [bucketFrom, bucketTo) 500원 단위 히스토그램 구간. */
	public record Bucket(int bucketFrom, int bucketTo, long count) {
	}

	public record RegionAvg(String sido, String sigungu, int avgPrice, long pharmacyCount) {
	}

	public record National(Integer avg, Integer median, Integer min, Integer max, Integer stdDev) {
	}
}
