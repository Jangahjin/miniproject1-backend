package com.pharmaprice.pharmacy.controller;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.dto.SigunguResponse;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.PharmacyRepository.RegionPharmacyCount;
import com.pharmaprice.pharmacy.repository.RegionRepository;

/** 위치 권한 거부 시 폴백 드롭다운용 지역 목록 (docs/API.md §7, docs/ROADMAP.md T-14). */
@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

	private final RegionRepository regionRepository;
	private final PharmacyRepository pharmacyRepository;

	public RegionController(RegionRepository regionRepository, PharmacyRepository pharmacyRepository) {
		this.regionRepository = regionRepository;
		this.pharmacyRepository = pharmacyRepository;
	}

	@GetMapping
	public ResponseEntity<List<RegionGroupResponse>> list() {
		Map<String, Long> pharmacyCountByRegion = pharmacyRepository.countGroupedByRegion().stream()
				.collect(Collectors.toMap(
						RegionPharmacyCount::getRegionCode, RegionPharmacyCount::getPharmacyCount));

		Map<String, List<SigunguResponse>> sigungusBySido = regionRepository.findAll().stream()
				.sorted(Comparator.comparing(r -> r.getSido() + r.getSigungu()))
				.collect(Collectors.groupingBy(
						r -> r.getSido(),
						LinkedHashMap::new,
						Collectors.mapping(
								r -> new SigunguResponse(
										r.getCode(), r.getSigungu(), r.getCenterLat(), r.getCenterLng(),
										pharmacyCountByRegion.getOrDefault(r.getCode(), 0L)),
								Collectors.toList())));

		List<RegionGroupResponse> groups = sigungusBySido.entrySet().stream()
				.map(entry -> new RegionGroupResponse(entry.getKey(), entry.getValue()))
				.toList();

		// 전체 ~250건, 페이지네이션 불필요. 위치 폴백용 정적에 가까운 데이터라 1시간 캐시한다.
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
				.body(groups);
	}
}
