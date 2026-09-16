package com.pharmaprice.drug.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;

/**
 * 검색 조건이 동적이고 pharmacy_drug_price_stat 집계가 필요해 JPQL 대신 native
 * SQL(NamedParameterJdbcTemplate)로 작성한다 (docs/ROADMAP.md T-13).
 */
@Repository
public class DrugQueryRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public DrugQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public PageResponse<DrugSummaryResponse> search(String q, String category, int page, int size) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String where = buildWhereClause(q, category, params);

		long totalElements = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM drug d " + where, params, Long.class);

		params.addValue("size", size);
		params.addValue("offset", page * size);

		// pharmacy_drug_price_stat 집계를 서브쿼리로 N+1 만들지 않고 조인 한 번으로 가져온다.
		String sql = "SELECT d.id, d.item_seq, d.display_name, d.name, d.maker, d.category, d.form, "
				+ "       d.package_unit, d.image_url, "
				+ "       ROUND(AVG(s.rep_price))::int AS national_avg_price, "
				+ "       COUNT(DISTINCT s.pharmacy_id) AS pharmacy_count "
				+ "FROM drug d "
				+ "LEFT JOIN pharmacy_drug_price_stat s ON s.drug_id = d.id "
				+ where
				+ " GROUP BY d.id "
				+ "ORDER BY d.id "
				+ "LIMIT :size OFFSET :offset";

		List<DrugSummaryResponse> content = jdbcTemplate.query(sql, params, DrugQueryRepository::mapSummary);
		return PageResponse.of(content, page, size, totalElements);
	}

	public Optional<DrugDetailResponse> findDetail(long drugId) {
		String sql = "SELECT d.id, d.item_seq, d.display_name, d.name, d.maker, d.category, d.form, "
				+ "       d.package_unit, d.image_url, "
				+ "       ROUND(AVG(s.rep_price))::int AS national_avg, "
				+ "       MIN(s.rep_price) AS national_min, "
				+ "       MAX(s.rep_price) AS national_max, "
				+ "       COUNT(DISTINCT s.pharmacy_id) AS pharmacy_count, "
				+ "       COALESCE(SUM(s.report_count), 0) AS report_count "
				+ "FROM drug d "
				+ "LEFT JOIN pharmacy_drug_price_stat s ON s.drug_id = d.id "
				+ "WHERE d.id = :drugId AND d.otc_flag = true "
				+ "GROUP BY d.id";

		MapSqlParameterSource params = new MapSqlParameterSource("drugId", drugId);
		return jdbcTemplate.query(sql, params, DrugQueryRepository::mapDetail).stream().findFirst();
	}

	private static String buildWhereClause(String q, String category, MapSqlParameterSource params) {
		StringBuilder where = new StringBuilder("WHERE d.otc_flag = true"); // 전문의약품은 어떤 경로로도 노출 금지 (PRD F1-6)

		if (StringUtils.hasText(q)) {
			// name에는 pg_trgm GIN 인덱스가 없지만(display_name만 있음), 의약품 40종 규모라 무시 가능한 비용이다.
			where.append(" AND (d.display_name ILIKE '%' || :q || '%' OR d.name ILIKE '%' || :q || '%')");
			params.addValue("q", q);
		}
		if (StringUtils.hasText(category)) {
			where.append(" AND d.category = :category");
			params.addValue("category", category);
		}
		return where.toString();
	}

	private static DrugSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
		return new DrugSummaryResponse(
				rs.getLong("id"),
				rs.getString("item_seq"),
				rs.getString("display_name"),
				rs.getString("name"),
				rs.getString("maker"),
				rs.getString("category"),
				rs.getString("form"),
				rs.getString("package_unit"),
				rs.getString("image_url"),
				rs.getObject("national_avg_price", Integer.class),
				rs.getInt("pharmacy_count"));
	}

	private static DrugDetailResponse mapDetail(ResultSet rs, int rowNum) throws SQLException {
		DrugDetailResponse.PriceStats priceStats = new DrugDetailResponse.PriceStats(
				rs.getObject("national_avg", Integer.class),
				rs.getObject("national_min", Integer.class),
				rs.getObject("national_max", Integer.class),
				rs.getInt("pharmacy_count"),
				rs.getInt("report_count"));
		return new DrugDetailResponse(
				rs.getLong("id"),
				rs.getString("item_seq"),
				rs.getString("display_name"),
				rs.getString("name"),
				rs.getString("maker"),
				rs.getString("category"),
				rs.getString("form"),
				rs.getString("package_unit"),
				rs.getString("image_url"),
				priceStats);
	}
}
