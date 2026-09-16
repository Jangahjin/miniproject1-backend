package com.pharmaprice.admin.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.pharmaprice.admin.dto.AdminOverviewResponse;
import com.pharmaprice.admin.dto.AdminOverviewResponse.Totals;
import com.pharmaprice.admin.dto.AdminOverviewResponse.TrendPoint;
import com.pharmaprice.admin.dto.DrugStatsResponse;
import com.pharmaprice.admin.dto.PriceGapsResponse;
import com.pharmaprice.admin.dto.RegionStatsResponse;
import com.pharmaprice.pharmacy.dto.RegionRefResponse;

/**
 * 관리자 통계 4종 (docs/API.md §8, docs/ROADMAP.md T-31, docs/DATABASE.md §5.3~5.4).
 * 전부 집계·동적 필터라 native SQL을 쓴다. "표본이 3건 미만인 지역은 제외한다"는
 * 규칙(가이드 6번)을 지역을 랭킹·비교하는 모든 쿼리(regions, drugs byRegion,
 * price-gaps)에 일관되게 적용한다 — 1개 약국짜리 지역이 "최저가 지역"으로
 * 뜨는 걸 막기 위함이다.
 */
@Repository
public class AdminStatsRepository {

	private static final int MIN_PHARMACY_SAMPLE = 3;
	private static final int MIN_REGION_SAMPLE = 3;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public AdminStatsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public AdminOverviewResponse overview() {
		long pharmacyCount = queryLong("SELECT COUNT(*) FROM pharmacy WHERE is_active = true");
		long drugCount = queryLong("SELECT COUNT(*) FROM drug WHERE otc_flag = true");
		long reportCount = queryLong("SELECT COUNT(*) FROM price_report");
		long userCount = queryLong("SELECT COUNT(*) FROM app_user");
		long coveredPairCount = queryLong("SELECT COUNT(*) FROM pharmacy_drug_price_stat");
		// status = 'ACTIVE'로 한정한다 — HIDDEN 처리된 제보까지 세면 관리자가 T-32에서
		// 아무리 조치해도(flagged 컬럼 자체는 안 건드리므로) 이 수치가 줄어들지 않아,
		// "숨김 처리 후 KPI가 갱신된다"(T-33 완료 판정)를 만족하지 못한다. 이 값은
		// "아직 검토가 필요한 이상치 수"를 의미하고, /admin/reports 목록의
		// flagged=true&status=ACTIVE 필터와도 같은 기준이다.
		long flaggedReportCount =
				queryLong("SELECT COUNT(*) FROM price_report WHERE flagged = true AND status = 'ACTIVE'");

		long totalPossiblePairs = pharmacyCount * drugCount;
		double coverageRate = totalPossiblePairs == 0
				? 0.0
				: Math.round((double) coveredPairCount / totalPossiblePairs * 100) / 100.0;

		Totals totals = new Totals(pharmacyCount, drugCount, reportCount, userCount, coveredPairCount);
		return new AdminOverviewResponse(totals, recentTrend(), flaggedReportCount, coverageRate);
	}

	private List<TrendPoint> recentTrend() {
		String sql = """
				SELECT created_at::date AS report_date, COUNT(*) AS cnt
				FROM price_report
				WHERE created_at::date >= CURRENT_DATE - 6
				GROUP BY report_date
				""";
		Map<LocalDate, Long> counts = new HashMap<>();
		jdbcTemplate.query(sql, rs -> {
			counts.put(rs.getObject("report_date", LocalDate.class), rs.getLong("cnt"));
		});

		LocalDate today = LocalDate.now(KST);
		List<TrendPoint> trend = new ArrayList<>();
		for (int i = 6; i >= 0; i--) {
			LocalDate date = today.minusDays(i);
			trend.add(new TrendPoint(date, counts.getOrDefault(date, 0L)));
		}
		return trend;
	}

	// 세 필터 모두 선택값이라 전부 비워 호출하면 :regionCode/:drugId/:sido가 전부 SQL NULL로
	// 바인딩된다. "$1 IS NULL"처럼 어떤 컬럼과도 비교되지 않는 자리는 PostgreSQL이 파라미터
	// 타입을 추론할 근거가 없어 "cannot determine data type of parameter"로 거부한다
	// (PharmacyRepository에서 겪은 "LIKE 재사용 시 lower(bytea)"와 같은 계열의 문제인데,
	// 여긴 잘못 추론하는 대신 아예 거부하는 쪽이다). 명시적 캐스팅으로 타입을 알려준다.
	public RegionStatsResponse regions(String regionCode, Long drugId, String sido) {
		String sql = """
				SELECT r.code AS region_code, r.sido, r.sigungu, d.id AS drug_id, d.display_name,
				       ROUND(AVG(s.rep_price))::int AS avg_price,
				       MIN(s.rep_price) AS min_price,
				       MAX(s.rep_price) AS max_price,
				       COUNT(DISTINCT s.pharmacy_id) AS pharmacy_count,
				       SUM(s.report_count) AS report_count
				FROM pharmacy_drug_price_stat s
				JOIN pharmacy p ON p.id = s.pharmacy_id
				JOIN region   r ON r.code = p.region_code
				JOIN drug     d ON d.id = s.drug_id
				WHERE (:regionCode::text   IS NULL OR p.region_code = :regionCode)
				  AND (:drugId::bigint     IS NULL OR s.drug_id = :drugId)
				  AND (:sido::text         IS NULL OR r.sido = :sido)
				GROUP BY r.code, r.sido, r.sigungu, d.id, d.display_name
				HAVING COUNT(DISTINCT s.pharmacy_id) >= :minSample
				ORDER BY r.sido, r.sigungu, d.display_name
				""";
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("regionCode", regionCode)
				.addValue("drugId", drugId)
				.addValue("sido", sido)
				.addValue("minSample", MIN_PHARMACY_SAMPLE);

		List<RegionStatsResponse.Row> rows = jdbcTemplate.query(sql, params, (rs, rowNum) -> new RegionStatsResponse.Row(
				new RegionRefResponse(rs.getString("region_code"), rs.getString("sido"), rs.getString("sigungu")),
				new RegionStatsResponse.DrugRef(rs.getLong("drug_id"), rs.getString("display_name")),
				rs.getInt("avg_price"), rs.getInt("min_price"), rs.getInt("max_price"),
				rs.getLong("pharmacy_count"), rs.getLong("report_count")));
		return new RegionStatsResponse(rows);
	}

	public Optional<DrugStatsResponse> drugStats(long drugId) {
		String drugSql = "SELECT id, display_name, package_unit FROM drug WHERE id = :drugId AND otc_flag = true";
		List<DrugStatsResponse.DrugRef> drugRows = jdbcTemplate.query(
				drugSql, new MapSqlParameterSource("drugId", drugId),
				(rs, rowNum) -> new DrugStatsResponse.DrugRef(
						rs.getLong("id"), rs.getString("display_name"), rs.getString("package_unit")));
		if (drugRows.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new DrugStatsResponse(
				drugRows.get(0), distribution(drugId), byRegion(drugId), national(drugId)));
	}

	private List<DrugStatsResponse.Bucket> distribution(long drugId) {
		// rep_price 500원 단위 버킷 — 개별 제보가 아니라 약국별 대표가격(rep_price) 기준.
		// 전국 평균/최소/최대(T-13 DrugQueryRepository)와 같은 기준을 쓴다.
		String sql = """
				SELECT (rep_price / 500) * 500 AS bucket_from, (rep_price / 500) * 500 + 500 AS bucket_to,
				       COUNT(*) AS cnt
				FROM pharmacy_drug_price_stat
				WHERE drug_id = :drugId
				GROUP BY bucket_from, bucket_to
				ORDER BY bucket_from
				""";
		return jdbcTemplate.query(sql, new MapSqlParameterSource("drugId", drugId), (rs, rowNum) ->
				new DrugStatsResponse.Bucket(rs.getInt("bucket_from"), rs.getInt("bucket_to"), rs.getLong("cnt")));
	}

	private List<DrugStatsResponse.RegionAvg> byRegion(long drugId) {
		String sql = """
				SELECT r.sido, r.sigungu, ROUND(AVG(s.rep_price))::int AS avg_price,
				       COUNT(DISTINCT s.pharmacy_id) AS pharmacy_count
				FROM pharmacy_drug_price_stat s
				JOIN pharmacy p ON p.id = s.pharmacy_id
				JOIN region   r ON r.code = p.region_code
				WHERE s.drug_id = :drugId
				GROUP BY r.sido, r.sigungu
				HAVING COUNT(DISTINCT s.pharmacy_id) >= :minSample
				ORDER BY avg_price ASC
				""";
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("drugId", drugId)
				.addValue("minSample", MIN_PHARMACY_SAMPLE);
		return jdbcTemplate.query(sql, params, (rs, rowNum) -> new DrugStatsResponse.RegionAvg(
				rs.getString("sido"), rs.getString("sigungu"), rs.getInt("avg_price"), rs.getLong("pharmacy_count")));
	}

	private DrugStatsResponse.National national(long drugId) {
		String sql = """
				SELECT ROUND(AVG(rep_price))::int AS avg_price,
				       ROUND(percentile_cont(0.5) WITHIN GROUP (ORDER BY rep_price))::int AS median_price,
				       MIN(rep_price) AS min_price,
				       MAX(rep_price) AS max_price,
				       ROUND(STDDEV(rep_price))::int AS std_dev
				FROM pharmacy_drug_price_stat
				WHERE drug_id = :drugId
				""";
		return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("drugId", drugId), (rs, rowNum) ->
				new DrugStatsResponse.National(
						rs.getObject("avg_price", Integer.class), rs.getObject("median_price", Integer.class),
						rs.getObject("min_price", Integer.class), rs.getObject("max_price", Integer.class),
						rs.getObject("std_dev", Integer.class)));
	}

	public PriceGapsResponse priceGaps(int limit) {
		// docs/DATABASE.md §5.4를 확장했다 — 원본은 격차(gap) 수치만 계산하고 어느 지역이
		// 최저/최고인지는 담지 않는데, API.md 응답은 그 지역 정보(sido/sigungu/avgPrice)까지
		// 요구해서 ROW_NUMBER()로 drug별 최저/최고 지역 행을 함께 뽑아낸다.
		String sql = """
				WITH region_avg AS (
				    SELECT s.drug_id, r.sido, r.sigungu, AVG(s.rep_price) AS avg_price, COUNT(*) AS pharmacy_count
				    FROM pharmacy_drug_price_stat s
				    JOIN pharmacy p ON p.id = s.pharmacy_id
				    JOIN region   r ON r.code = p.region_code
				    GROUP BY s.drug_id, r.sido, r.sigungu
				    HAVING COUNT(*) >= :minPharmacySample
				),
				eligible_drugs AS (
				    SELECT drug_id FROM region_avg GROUP BY drug_id HAVING COUNT(*) >= :minRegionSample
				),
				ranked AS (
				    SELECT ra.*,
				           ROW_NUMBER() OVER (PARTITION BY ra.drug_id ORDER BY ra.avg_price ASC)  AS rn_cheap,
				           ROW_NUMBER() OVER (PARTITION BY ra.drug_id ORDER BY ra.avg_price DESC) AS rn_pricey
				    FROM region_avg ra
				    JOIN eligible_drugs ed ON ed.drug_id = ra.drug_id
				)
				SELECT
				    d.id AS drug_id, d.display_name,
				    cheap.sido AS cheap_sido, cheap.sigungu AS cheap_sigungu,
				    ROUND(cheap.avg_price)::int AS cheap_avg,
				    pricey.sido AS pricey_sido, pricey.sigungu AS pricey_sigungu,
				    ROUND(pricey.avg_price)::int AS pricey_avg,
				    ROUND(pricey.avg_price - cheap.avg_price)::int AS gap,
				    ROUND(((pricey.avg_price - cheap.avg_price) / cheap.avg_price * 100)::numeric, 1) AS gap_pct
				FROM ranked cheap
				JOIN ranked pricey ON pricey.drug_id = cheap.drug_id AND pricey.rn_pricey = 1
				JOIN drug d ON d.id = cheap.drug_id
				WHERE cheap.rn_cheap = 1
				ORDER BY gap_pct DESC
				LIMIT :limit
				""";
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("minPharmacySample", MIN_PHARMACY_SAMPLE)
				.addValue("minRegionSample", MIN_REGION_SAMPLE)
				.addValue("limit", limit);

		List<PriceGapsResponse.Row> rows = jdbcTemplate.query(sql, params, (rs, rowNum) -> new PriceGapsResponse.Row(
				new PriceGapsResponse.DrugRef(rs.getLong("drug_id"), rs.getString("display_name")),
				new PriceGapsResponse.RegionAvg(
						rs.getString("cheap_sido"), rs.getString("cheap_sigungu"), rs.getInt("cheap_avg")),
				new PriceGapsResponse.RegionAvg(
						rs.getString("pricey_sido"), rs.getString("pricey_sigungu"), rs.getInt("pricey_avg")),
				rs.getInt("gap"), rs.getDouble("gap_pct")));
		return new PriceGapsResponse(rows);
	}

	private long queryLong(String sql) {
		Long result = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Long.class);
		return result != null ? result : 0L;
	}
}
