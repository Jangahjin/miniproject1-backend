package com.pharmaprice.recommendation.service;

import java.util.Optional;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;

public interface PriceStatService {

	/** 해당 조합의 통계를 재계산해 upsert하고, 유효 제보가 0이면 삭제한다. */
	Optional<PharmacyDrugPriceStat> recalculate(long pharmacyId, long drugId);
}
