package com.pharmaprice.recommendation.repository;

import java.time.LocalDate;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * pharmacy_drug_price_stat 재계산 전용 native SQL 리포지토리. percentile_cont 기반
 * IQR 제거·중앙값 계산과 upsert는 JPQL로 표현할 수 없어 직접 작성한다 (docs/DATABASE.md §5.2).
 */
@Repository
public class PriceStatRepository {

	private static final String AGGREGATE_SQL = """
			WITH valid AS (
			    SELECT price, purchased_at
			    FROM price_report
			    WHERE pharmacy_id = :pharmacyId
			      AND drug_id = :drugId
			      AND status = 'ACTIVE'
			      AND flagged = false
			      AND purchased_at >= CURRENT_DATE - :windowDays
			),
			q AS (
			    SELECT
			        percentile_cont(0.25) WITHIN GROUP (ORDER BY price) AS q1,
			        percentile_cont(0.75) WITHIN GROUP (ORDER BY price) AS q3,
			        count(*) AS n
			    FROM valid
			),
			trimmed AS (
			    SELECT v.price
			    FROM valid v CROSS JOIN q
			    WHERE q.n < 4
			       OR v.price BETWEEN q.q1 - 1.5 * (q.q3 - q.q1)
			                      AND q.q3 + 1.5 * (q.q3 - q.q1)
			)
			SELECT
			    percentile_cont(0.5) WITHIN GROUP (ORDER BY t.price)::int AS rep_price,
			    MIN(t.price)::int AS min_price,
			    MAX(t.price)::int AS max_price,
			    AVG(t.price)::int AS avg_price,
			    COUNT(*)::int AS report_count,
			    (SELECT MAX(v2.purchased_at) FROM valid v2) AS last_reported_at
			FROM trimmed t
			""";

	private static final String UPSERT_SQL = """
			INSERT INTO pharmacy_drug_price_stat
			    (pharmacy_id, drug_id, rep_price, min_price, max_price, avg_price,
			     report_count, last_reported_at, window_days, calculated_at)
			VALUES
			    (:pharmacyId, :drugId, :repPrice, :minPrice, :maxPrice, :avgPrice,
			     :reportCount, :lastReportedAt, :windowDays, now())
			ON CONFLICT (pharmacy_id, drug_id) DO UPDATE SET
			    rep_price = EXCLUDED.rep_price,
			    min_price = EXCLUDED.min_price,
			    max_price = EXCLUDED.max_price,
			    avg_price = EXCLUDED.avg_price,
			    report_count = EXCLUDED.report_count,
			    last_reported_at = EXCLUDED.last_reported_at,
			    window_days = EXCLUDED.window_days,
			    calculated_at = EXCLUDED.calculated_at
			""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public PriceStatRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Aggregate aggregate(long pharmacyId, long drugId, int windowDays) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("pharmacyId", pharmacyId)
				.addValue("drugId", drugId)
				.addValue("windowDays", windowDays);

		return jdbcTemplate.queryForObject(AGGREGATE_SQL, params, (rs, rowNum) -> new Aggregate(
				rs.getObject("rep_price", Integer.class),
				rs.getObject("min_price", Integer.class),
				rs.getObject("max_price", Integer.class),
				rs.getObject("avg_price", Integer.class),
				rs.getInt("report_count"),
				rs.getObject("last_reported_at", LocalDate.class)));
	}

	public void upsert(long pharmacyId, long drugId, Aggregate aggregate, int windowDays) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("pharmacyId", pharmacyId)
				.addValue("drugId", drugId)
				.addValue("repPrice", aggregate.repPrice())
				.addValue("minPrice", aggregate.minPrice())
				.addValue("maxPrice", aggregate.maxPrice())
				.addValue("avgPrice", aggregate.avgPrice())
				.addValue("reportCount", aggregate.reportCount())
				.addValue("lastReportedAt", aggregate.lastReportedAt())
				.addValue("windowDays", windowDays);

		jdbcTemplate.update(UPSERT_SQL, params);
	}

	public record Aggregate(
			Integer repPrice, Integer minPrice, Integer maxPrice, Integer avgPrice,
			int reportCount, LocalDate lastReportedAt) {

		public boolean isEmpty() {
			return reportCount == 0;
		}
	}
}
