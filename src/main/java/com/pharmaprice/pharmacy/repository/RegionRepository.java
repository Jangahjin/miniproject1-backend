package com.pharmaprice.pharmacy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pharmaprice.pharmacy.domain.Region;

public interface RegionRepository extends JpaRepository<Region, String> {
}
