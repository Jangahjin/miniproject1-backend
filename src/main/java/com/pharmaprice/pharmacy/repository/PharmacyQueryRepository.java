package com.pharmaprice.pharmacy.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse.DrugPriceItem;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.RegionRefResponse;
import com.pharmaprice.recommendation.distance.DistanceCalculator;

/**
 * 약국 검색·상세 (docs/API.md §4, docs/ROADMAP.md T-19). 약국은 전체 ~250건 규모라
 * DB에서는 이름·활성 여부만 거르고, 거리 계산·반경 필터·정렬은 T-09
 * DistanceCalculator를 재사용해 Java 레이어에서 처리한다 — 여기서 Haversine을
 * 다시 구현하지 않는다.
 */
@Repository
public class PharmacyQueryRepository {

	private final PharmacyRepository pharmacyRepository;
	private final DistanceCalculator distanceCalculator;
	private final NamedParameterJdbcTemplate jdbcTemplate;

	public PharmacyQueryRepository(
			PharmacyRepository pharmacyRepository,
			DistanceCalculator distanceCalculator,
			NamedParameterJdbcTemplate jdbcTemplate) {
		this.pharmacyRepository = pharmacyRepository;
		this.distanceCalculator = distanceCalculator;
		this.jdbcTemplate = jdbcTemplate;
	}

	public PageResponse<PharmacySummaryResponse> search(
			String q, Double lat, Double lng, int radius, int page, int size) {
		boolean hasLocation = lat != null && lng != null;
		List<Pharmacy> candidates = StringUtils.hasText(q)
				? pharmacyRepository.searchActiveByNameOrAddress(q)
				: pharmacyRepository.findAllActiveWithRegion();

		List<PharmacySummaryResponse> matched = candidates.stream()
				.map(p -> toSummary(p, hasLocation
						? distanceCalculator.distanceMeters(lat, lng, p.getLat(), p.getLng())
						: null))
				.filter(item -> !hasLocation || item.distanceM() <= radius)
				.sorted(hasLocation
						? Comparator.comparingDouble(PharmacySummaryResponse::distanceM)
						: Comparator.comparingLong(PharmacySummaryResponse::id))
				.toList();

		int fromIndex = Math.min(page * size, matched.size());
		int toIndex = Math.min(fromIndex + size, matched.size());
		return PageResponse.of(matched.subList(fromIndex, toIndex), page, size, matched.size());
	}

	public Optional<PharmacyDetailResponse> findDetail(long pharmacyId, Double lat, Double lng) {
		return pharmacyRepository.findActiveByIdWithRegion(pharmacyId)
				.map(p -> {
					Double distanceM = (lat != null && lng != null)
							? distanceCalculator.distanceMeters(lat, lng, p.getLat(), p.getLng())
							: null;
					return new PharmacyDetailResponse(
							p.getId(), p.getName(), p.getAddressRoad(), p.getAddressJibun(),
							p.getLat(), p.getLng(), p.getPhone(), p.getBusinessHours(),
							distanceM, toRegionRef(p.getRegion()), findDrugPrices(pharmacyId));
				});
	}

	// national_avg_price는 pharmacy_drug_price_stat 전체를 drug_id로 묶은 서브쿼리라 JPA로는
	// 표현이 번거로워 native SQL을 쓴다 (docs/ROADMAP.md T-13의 DrugQueryRepository와 같은 이유).
	private List<DrugPriceItem> findDrugPrices(long pharmacyId) {
		String sql = """
				SELECT d.id AS drug_id, d.display_name, d.package_unit, d.category,
				       s.rep_price, s.min_price, s.max_price, s.avg_price,
				       s.report_count, s.last_reported_at,
				       nat.national_avg_price
				FROM pharmacy_drug_price_stat s
				JOIN drug d ON d.id = s.drug_id
				JOIN (
				    SELECT drug_id, ROUND(AVG(rep_price))::int AS national_avg_price
				    FROM pharmacy_drug_price_stat
				    GROUP BY drug_id
				) nat ON nat.drug_id = s.drug_id
				WHERE s.pharmacy_id = :pharmacyId
				ORDER BY s.rep_price ASC
				""";
		return jdbcTemplate.query(sql, new MapSqlParameterSource("pharmacyId", pharmacyId),
				PharmacyQueryRepository::mapDrugPrice);
	}

	private static DrugPriceItem mapDrugPrice(ResultSet rs, int rowNum) throws SQLException {
		int repPrice = rs.getInt("rep_price");
		int nationalAvgPrice = rs.getInt("national_avg_price");
		return new DrugPriceItem(
				rs.getLong("drug_id"), rs.getString("display_name"), rs.getString("package_unit"),
				rs.getString("category"),
				repPrice, rs.getInt("min_price"), rs.getInt("max_price"), rs.getInt("avg_price"),
				rs.getInt("report_count"), rs.getObject("last_reported_at", LocalDate.class),
				nationalAvgPrice, repPrice - nationalAvgPrice);
	}

	private static PharmacySummaryResponse toSummary(Pharmacy p, Double distanceM) {
		return new PharmacySummaryResponse(
				p.getId(), p.getName(), p.getAddressRoad(), p.getLat(), p.getLng(), p.getPhone(),
				distanceM, toRegionRef(p.getRegion()));
	}

	private static RegionRefResponse toRegionRef(Region region) {
		return new RegionRefResponse(region.getCode(), region.getSido(), region.getSigungu());
	}
}
