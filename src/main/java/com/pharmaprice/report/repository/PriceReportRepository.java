package com.pharmaprice.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pharmaprice.report.domain.PriceReport;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {
}
