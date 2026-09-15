package com.pharmaprice.recommendation.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.pharmacy.domain.Pharmacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검색 경로에서 읽는 유일한 집계 테이블. price_report가 변경될 때마다 해당 행만 재계산한다 (T-10).
 */
@Entity
@Table(name = "pharmacy_drug_price_stat")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PharmacyDrugPriceStat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pharmacy_id", nullable = false)
	private Pharmacy pharmacy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "drug_id", nullable = false)
	private Drug drug;

	@Column(name = "rep_price", nullable = false)
	private int repPrice;

	@Column(name = "min_price", nullable = false)
	private int minPrice;

	@Column(name = "max_price", nullable = false)
	private int maxPrice;

	@Column(name = "avg_price", nullable = false)
	private int avgPrice;

	@Column(name = "report_count", nullable = false)
	private int reportCount;

	@Column(name = "last_reported_at", nullable = false)
	private LocalDate lastReportedAt;

	@Column(name = "window_days", nullable = false)
	private short windowDays;

	@Column(name = "calculated_at", nullable = false)
	private Instant calculatedAt;
}
