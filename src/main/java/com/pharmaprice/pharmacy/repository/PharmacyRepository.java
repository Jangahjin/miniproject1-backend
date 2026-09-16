package com.pharmaprice.pharmacy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pharmaprice.pharmacy.domain.Pharmacy;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

	@Query("SELECT p.region.code AS regionCode, COUNT(p) AS pharmacyCount "
			+ "FROM Pharmacy p GROUP BY p.region.code")
	List<RegionPharmacyCount> countGroupedByRegion();

	// 약국 ~250건 규모라 region을 JOIN FETCH로 한 번에 당겨와 N+1을 막는다 (docs/ROADMAP.md T-19).
	// q가 null일 때 ":q IS NULL OR ..." 한 파라미터를 재사용하면 PostgreSQL이 그 중 한 자리의
	// 타입을 추론하지 못해 "lower(bytea)" 오류가 나므로, q 유무에 따라 메서드를 아예 분리한다.
	@Query("SELECT p FROM Pharmacy p JOIN FETCH p.region WHERE p.active = true")
	List<Pharmacy> findAllActiveWithRegion();

	@Query("SELECT p FROM Pharmacy p JOIN FETCH p.region WHERE p.active = true "
			+ "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
			+ "OR LOWER(p.addressRoad) LIKE LOWER(CONCAT('%', :q, '%')))")
	List<Pharmacy> searchActiveByNameOrAddress(@Param("q") String q);

	// region이 LAZY라 findById만으로는 리포지토리 메서드 밖에서 접근 시 세션이 끊겨 있다.
	// 상세 조회는 단건이라 JOIN FETCH로 한 번에 가져온다 (docs/ROADMAP.md T-19).
	@Query("SELECT p FROM Pharmacy p JOIN FETCH p.region WHERE p.id = :id AND p.active = true")
	Optional<Pharmacy> findActiveByIdWithRegion(@Param("id") long id);

	interface RegionPharmacyCount {
		String getRegionCode();

		long getPharmacyCount();
	}
}
