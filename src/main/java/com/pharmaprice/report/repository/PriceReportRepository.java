package com.pharmaprice.report.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;

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

	// 목록 조회(docs/ROADMAP.md T-28, T-32 공용). pharmacy/drug/user는 모두 단일값
	// (@ManyToOne) 연관이라 JOIN FETCH + Pageable을 함께 써도 컬렉션 페치처럼
	// 인메모리 페이징이 되지 않는다 — 안전하게 DB 레벨 LIMIT/OFFSET 페이징이 유지된다.
	// JPQL(HQL) 파라미터는 Hibernate가 타입을 알고 바인딩하므로, native SQL에서
	// AdminStatsRepository가 겪은 "전부 null이면 타입을 못 정한다" 문제가 없다.
	@Query(
			value = """
					SELECT r FROM PriceReport r
					JOIN FETCH r.pharmacy
					JOIN FETCH r.drug
					LEFT JOIN FETCH r.user
					WHERE (:pharmacyId IS NULL OR r.pharmacy.id = :pharmacyId)
					  AND (:drugId IS NULL OR r.drug.id = :drugId)
					  AND (:userId IS NULL OR r.user.id = :userId)
					  AND (:flagged IS NULL OR r.flagged = :flagged)
					  AND (:status IS NULL OR r.status = :status)
					ORDER BY r.createdAt DESC
					""",
			countQuery = """
					SELECT COUNT(r) FROM PriceReport r
					WHERE (:pharmacyId IS NULL OR r.pharmacy.id = :pharmacyId)
					  AND (:drugId IS NULL OR r.drug.id = :drugId)
					  AND (:userId IS NULL OR r.user.id = :userId)
					  AND (:flagged IS NULL OR r.flagged = :flagged)
					  AND (:status IS NULL OR r.status = :status)
					""")
	Page<PriceReport> search(
			@Param("pharmacyId") Long pharmacyId,
			@Param("drugId") Long drugId,
			@Param("userId") Long userId,
			@Param("flagged") Boolean flagged,
			@Param("status") ReportStatus status,
			Pageable pageable);
}
