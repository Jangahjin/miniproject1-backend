package com.pharmaprice.pharmacy.domain;

import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.pharmaprice.common.domain.BaseCreatedAtEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "pharmacy")
@Getter
@NoArgsConstructor
@SuperBuilder
public class Pharmacy extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "hira_code", unique = true, length = 30)
	private String hiraCode;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "address_road", length = 255)
	private String addressRoad;

	@Column(name = "address_jibun", length = 255)
	private String addressJibun;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "region_code")
	private Region region;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lng;

	@Column(length = 20)
	private String phone;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "business_hours", columnDefinition = "jsonb")
	private Map<String, List<String>> businessHours;

	@Column(name = "is_active", nullable = false)
	@Builder.Default
	private boolean active = true;
}
