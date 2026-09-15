package com.pharmaprice.pharmacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "region")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Region {

	@Id
	@Column(length = 10)
	private String code;

	@Column(nullable = false, length = 20)
	private String sido;

	@Column(nullable = false, length = 30)
	private String sigungu;

	@Column(name = "center_lat", nullable = false)
	private double centerLat;

	@Column(name = "center_lng", nullable = false)
	private double centerLng;
}
