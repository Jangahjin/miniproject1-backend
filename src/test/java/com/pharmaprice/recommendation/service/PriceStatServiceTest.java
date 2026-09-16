package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.repository.PriceReportRepository;

class PriceStatServiceTest extends AbstractIntegrationTest {

	@Autowired
	PriceStatService priceStatService;
	@Autowired
	RegionRepository regionRepository;
	@Autowired
	PharmacyRepository pharmacyRepository;
	@Autowired
	DrugRepository drugRepository;
	@Autowired
	PriceReportRepository priceReportRepository;
	@Autowired
	PharmacyDrugPriceStatRepository statRepository;

	Long pharmacyId;
	Long drugId;

	@BeforeEach
	void setUp() {
		Region region = regionRepository.save(Region.builder()
				.code("11680").sido("서울특별시").sigungu("강남구")
				.centerLat(37.4979).centerLng(127.0276).build());
		Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
				.name("행복약국").region(region).lat(37.4979).lng(127.0276).build());
		Drug drug = drugRepository.save(Drug.builder()
				.name("타이레놀정500밀리그람").displayName("타이레놀 500mg")
				.category("해열진통").packageUnit("8정").build());
		pharmacyId = pharmacy.getId();
		drugId = drug.getId();
	}

	private void saveReport(int price, LocalDate purchasedAt) {
		priceReportRepository.save(PriceReport.builder()
				.pharmacy(pharmacyRepository.getReferenceById(pharmacyId))
				.drug(drugRepository.getReferenceById(drugId))
				.price(price).purchasedAt(purchasedAt).build());
	}

	@Test
	void 극단값이_섞여도_중앙값이_흔들리지_않는다() {
		LocalDate today = LocalDate.now();
		List.of(2800, 2800, 2800, 2800, 9999).forEach(price -> saveReport(price, today));

		PharmacyDrugPriceStat stat = priceStatService.recalculate(pharmacyId, drugId).orElseThrow();

		assertThat(stat.getRepPrice()).isEqualTo(2800);
		assertThat(stat.getReportCount()).isEqualTo(4); // 9999는 IQR로 제거됨
		assertThat(stat.getWindowDays()).isEqualTo((short) 90);
	}

	@Test
	void 표본이_4건_미만이면_IQR_제거_없이_중앙값을_그대로_쓴다() {
		LocalDate today = LocalDate.now();
		List.of(2800, 3000, 3200).forEach(price -> saveReport(price, today));

		PharmacyDrugPriceStat stat = priceStatService.recalculate(pharmacyId, drugId).orElseThrow();

		assertThat(stat.getRepPrice()).isEqualTo(3000);
		assertThat(stat.getReportCount()).isEqualTo(3); // 3건 전부 유지
	}

	@Test
	void 구십일_내_제보가_없으면_백팔십일_창으로_확대된다() {
		saveReport(2500, LocalDate.now().minusDays(100)); // 90일 밖, 180일 안

		PharmacyDrugPriceStat stat = priceStatService.recalculate(pharmacyId, drugId).orElseThrow();

		assertThat(stat.getRepPrice()).isEqualTo(2500);
		assertThat(stat.getWindowDays()).isEqualTo((short) 180);
	}

	@Test
	void 유효_제보가_없으면_기존_통계_행이_삭제된다() {
		statRepository.save(PharmacyDrugPriceStat.builder()
				.pharmacy(pharmacyRepository.getReferenceById(pharmacyId))
				.drug(drugRepository.getReferenceById(drugId))
				.repPrice(2800).minPrice(2800).maxPrice(2800).avgPrice(2800)
				.reportCount(1).lastReportedAt(LocalDate.now())
				.windowDays((short) 90).calculatedAt(java.time.Instant.now())
				.build());
		saveReport(2500, LocalDate.now().minusDays(200)); // 180일 밖

		Optional<PharmacyDrugPriceStat> result = priceStatService.recalculate(pharmacyId, drugId);

		assertThat(result).isEmpty();
		assertThat(statRepository.findByPharmacyIdAndDrugId(pharmacyId, drugId)).isEmpty();
	}

	@Test
	void 같은_조합을_두번_재계산해도_결과가_동일하다() {
		LocalDate today = LocalDate.now();
		List.of(2800, 2800, 2800, 2800, 9999).forEach(price -> saveReport(price, today));

		PharmacyDrugPriceStat first = priceStatService.recalculate(pharmacyId, drugId).orElseThrow();
		PharmacyDrugPriceStat second = priceStatService.recalculate(pharmacyId, drugId).orElseThrow();

		assertThat(second.getId()).isEqualTo(first.getId()); // 같은 행을 갱신했을 뿐, 새 행이 생기지 않는다
		assertThat(second.getRepPrice()).isEqualTo(first.getRepPrice());
		assertThat(second.getMinPrice()).isEqualTo(first.getMinPrice());
		assertThat(second.getMaxPrice()).isEqualTo(first.getMaxPrice());
		assertThat(second.getReportCount()).isEqualTo(first.getReportCount());
	}
}
