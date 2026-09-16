package com.pharmaprice.recommendation.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.recommendation.repository.PriceStatRepository.Aggregate;

import jakarta.persistence.EntityManager;

@Service
public class PriceStatServiceImpl implements PriceStatService {

	private final PriceStatRepository priceStatRepository;
	private final PharmacyDrugPriceStatRepository statRepository;
	private final RecommendationProperties properties;
	private final EntityManager entityManager;

	public PriceStatServiceImpl(
			PriceStatRepository priceStatRepository,
			PharmacyDrugPriceStatRepository statRepository,
			RecommendationProperties properties,
			EntityManager entityManager) {
		this.priceStatRepository = priceStatRepository;
		this.statRepository = statRepository;
		this.properties = properties;
		this.entityManager = entityManager;
	}

	@Override
	@Transactional
	public Optional<PharmacyDrugPriceStat> recalculate(long pharmacyId, long drugId) {
		int primaryWindow = properties.priceWindowDays();
		int fallbackWindow = properties.priceWindowFallbackDays();

		Aggregate aggregate = priceStatRepository.aggregate(pharmacyId, drugId, primaryWindow);
		int windowDays = primaryWindow;

		if (aggregate.isEmpty()) {
			aggregate = priceStatRepository.aggregate(pharmacyId, drugId, fallbackWindow);
			windowDays = fallbackWindow;
		}

		if (aggregate.isEmpty()) {
			statRepository.deleteByPharmacyIdAndDrugId(pharmacyId, drugId);
			return Optional.empty();
		}

		priceStatRepository.upsert(pharmacyId, drugId, aggregate, windowDays);

		// upsert는 native SQL이라 영속성 컨텍스트를 거치지 않는다. 이전에 같은 조합을
		// 조회해 캐시된 엔티티가 있으면 그 값으로 되돌아오므로 반드시 비우고 다시 읽는다.
		entityManager.clear();
		return statRepository.findByPharmacyIdAndDrugId(pharmacyId, drugId);
	}
}
