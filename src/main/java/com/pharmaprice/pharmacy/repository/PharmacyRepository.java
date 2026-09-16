package com.pharmaprice.pharmacy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.pharmaprice.pharmacy.domain.Pharmacy;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

	@Query("SELECT p.region.code AS regionCode, COUNT(p) AS pharmacyCount "
			+ "FROM Pharmacy p GROUP BY p.region.code")
	List<RegionPharmacyCount> countGroupedByRegion();

	interface RegionPharmacyCount {
		String getRegionCode();

		long getPharmacyCount();
	}
}
