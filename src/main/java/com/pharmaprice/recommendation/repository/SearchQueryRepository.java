package com.pharmaprice.recommendation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.pharmaprice.recommendation.distance.DistanceCalculator.BoundingBox;

/**
 * 최저가 검색 후보 조회 (docs/DATABASE.md §5.1). Score 계산·정렬은 Java 서비스
 * 레이어(ScoreCalculator, T-11)에서 하므로 여기서는 거리·가격 통계만 가져온다.
 */
@Repository
public class SearchQueryRepository {

	private static final String CANDIDATES_SQL = """
			SELECT p.id AS pharmacy_id, p.name, p.address_road, p.lat, p.lng, p.phone,
			       s.rep_price, s.min_price, s.avg_price, s.report_count, s.last_reported_at,
			       6371000 * 2 * asin(sqrt(
			           power(sin(radians(p.lat - :latU) / 2), 2)
			         + cos(radians(:latU)) * cos(radians(p.lat))
			         * power(sin(radians(p.lng - :lngU) / 2), 2)
			       )) AS distance_m
			FROM pharmacy_drug_price_stat s
			JOIN pharmacy p ON p.id = s.pharmacy_id
			WHERE s.drug_id = :drugId
			  AND p.is_active = true
			  AND p.lat BETWEEN :latMin AND :latMax
			  AND p.lng BETWEEN :lngMin AND :lngMax
			""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public SearchQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 바운딩 박스로 1차 후보를 가져온 뒤, 모서리에 섞여 들어온 반경 밖 약국을 정확 거리로 걸러낸다. */
	public List<CandidateRow> findCandidates(long drugId, double latU, double lngU, BoundingBox box, int radiusM) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("drugId", drugId)
				.addValue("latU", latU)
				.addValue("lngU", lngU)
				.addValue("latMin", box.minLat())
				.addValue("latMax", box.maxLat())
				.addValue("lngMin", box.minLng())
				.addValue("lngMax", box.maxLng());

		List<CandidateRow> nearby = jdbcTemplate.query(CANDIDATES_SQL, params, SearchQueryRepository::mapRow);
		return nearby.stream().filter(r -> r.distanceM() <= radiusM).toList();
	}

	/** 결과에 포함된 (약국, 약품) 쌍들의 제보 출처. dataSource(SEED/MIXED/USER) 판정에 쓰인다. */
	public List<String> findDistinctSources(long drugId, List<Long> pharmacyIds) {
		if (pharmacyIds.isEmpty()) {
			return List.of();
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("drugId", drugId)
				.addValue("pharmacyIds", pharmacyIds);
		return jdbcTemplate.queryForList(
				"SELECT DISTINCT source FROM price_report "
						+ "WHERE drug_id = :drugId AND pharmacy_id IN (:pharmacyIds) "
						+ "AND status = 'ACTIVE' AND flagged = false",
				params, String.class);
	}

	private static CandidateRow mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new CandidateRow(
				rs.getLong("pharmacy_id"), rs.getString("name"), rs.getString("address_road"),
				rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("phone"),
				rs.getInt("rep_price"), rs.getInt("min_price"), rs.getInt("avg_price"),
				rs.getInt("report_count"), rs.getObject("last_reported_at", LocalDate.class),
				rs.getDouble("distance_m"));
	}

	public record CandidateRow(
			long pharmacyId, String name, String addressRoad, double lat, double lng, String phone,
			int repPrice, int minPrice, int avgPrice, int reportCount, LocalDate lastReportedAt,
			double distanceM) {
	}
}
