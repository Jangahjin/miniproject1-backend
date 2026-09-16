package com.pharmaprice.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pharmaprice.report.domain.PriceReport;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {

	// 이상치 판정 기준(docs/ROADMAP.md T-26) — 특정 약국이 아니라 해당 약품 전체(모든 약국)의
	// 중앙값과 비교한다. percentile_cont는 JPQL로 표현할 수 없어 native SQL을 쓴다.
	// 유효 제보가 하나도 없으면 percentile_cont가 NULL을 반환한다.
	@Query(
			value = """
					SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY price)
					FROM price_report
					WHERE drug_id = :drugId AND status = 'ACTIVE' AND flagged = false
					""",
			nativeQuery = true)
	Double findMedianPriceByDrugId(@Param("drugId") long drugId);
}
