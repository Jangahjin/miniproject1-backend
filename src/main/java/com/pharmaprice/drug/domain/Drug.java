package com.pharmaprice.drug.domain;

import com.pharmaprice.common.domain.BaseCreatedAtEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "drug")
@Getter
@NoArgsConstructor
@SuperBuilder
public class Drug extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "item_seq", unique = true, length = 20)
	private String itemSeq;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(length = 100)
	private String maker;

	@Column(nullable = false, length = 50)
	private String category;

	@Column(length = 50)
	private String form;

	@Column(name = "package_unit", nullable = false, length = 50)
	private String packageUnit;

	@Column(name = "otc_flag", nullable = false)
	@Builder.Default
	private boolean otcFlag = true;

	@Column(name = "base_price")
	private Integer basePrice;

	@Column(name = "image_url", length = 500)
	private String imageUrl;
}
