package com.pharmaprice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.RefreshToken;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.domain.UserStatus;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.repository.RefreshTokenRepository;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.report.repository.UploadedFileRepository;

import jakarta.persistence.EntityManager;

class EntityPersistenceTest extends AbstractIntegrationTest {

	@Autowired
	EntityManager em;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	RegionRepository regionRepository;
	@Autowired
	PharmacyRepository pharmacyRepository;
	@Autowired
	DrugRepository drugRepository;
	@Autowired
	AppUserRepository appUserRepository;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;
	@Autowired
	UploadedFileRepository uploadedFileRepository;
	@Autowired
	PriceReportRepository priceReportRepository;
	@Autowired
	PharmacyDrugPriceStatRepository priceStatRepository;

	private Region saveRegion() {
		return regionRepository.save(Region.builder()
				.code("11680").sido("서울특별시").sigungu("강남구")
				.centerLat(37.4979).centerLng(127.0276).build());
	}

	@Test
	void region_저장_조회() {
		Region saved = saveRegion();
		em.flush();
		em.clear();

		Region found = regionRepository.findById(saved.getCode()).orElseThrow();
		assertThat(found.getSigungu()).isEqualTo("강남구");
	}

	@Test
	void pharmacy_저장_조회_businessHours_JSONB_왕복() {
		Region region = saveRegion();
		Map<String, List<String>> hours = Map.of(
				"mon", List.of("09:00", "19:00"),
				"holiday", List.of());

		Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
				.name("행복약국").region(region).lat(37.4979).lng(127.0276)
				.businessHours(hours).build());
		Long id = pharmacy.getId();
		em.flush();
		em.clear();

		Pharmacy found = pharmacyRepository.findById(id).orElseThrow();
		assertThat(found.getName()).isEqualTo("행복약국");
		assertThat(found.isActive()).isTrue();
		assertThat(found.getBusinessHours()).containsEntry("mon", List.of("09:00", "19:00"));
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void drug_저장_조회() {
		Drug drug = drugRepository.save(Drug.builder()
				.name("타이레놀정500밀리그람").displayName("타이레놀 500mg")
				.category("해열진통").packageUnit("8정").build());
		Long id = drug.getId();
		em.flush();
		em.clear();

		Drug found = drugRepository.findById(id).orElseThrow();
		assertThat(found.getPackageUnit()).isEqualTo("8정");
		assertThat(found.isOtcFlag()).isTrue();
	}

	@Test
	void appUser_저장_조회_enum_role_status() {
		AppUser user = appUserRepository.save(AppUser.builder()
				.email("user01@example.com").passwordHash("hash")
				.nickname("닉네임").build());
		Long id = user.getId();
		em.flush();
		em.clear();

		AppUser found = appUserRepository.findById(id).orElseThrow();
		assertThat(found.getRole()).isEqualTo(UserRole.USER);
		assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();

		String rawRole = jdbcTemplate.queryForObject(
				"SELECT role FROM app_user WHERE id = ?", String.class, id);
		assertThat(rawRole).isEqualTo("USER");
	}

	@Test
	void refreshToken_저장_조회() {
		AppUser user = appUserRepository.save(AppUser.builder()
				.email("user02@example.com").passwordHash("hash").nickname("닉네임2").build());

		RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
				.user(user).tokenHash("hash-value").expiresAt(Instant.now().plusSeconds(3600)).build());
		Long id = token.getId();
		em.flush();
		em.clear();

		RefreshToken found = refreshTokenRepository.findById(id).orElseThrow();
		assertThat(found.getTokenHash()).isEqualTo("hash-value");
		assertThat(found.getRevokedAt()).isNull();
	}

	@Test
	void uploadedFile_저장_조회() {
		AppUser user = appUserRepository.save(AppUser.builder()
				.email("user03@example.com").passwordHash("hash").nickname("닉네임3").build());

		UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
				.originalName("receipt.jpg").storedPath("uploads/2026/09/uuid.jpg")
				.contentType("image/jpeg").sizeBytes(1024).uploadedBy(user).build());
		Long id = file.getId();
		em.flush();
		em.clear();

		UploadedFile found = uploadedFileRepository.findById(id).orElseThrow();
		assertThat(found.getContentType()).isEqualTo("image/jpeg");
	}

	@Test
	void priceReport_저장_조회_enum이_문자열로_저장된다() {
		Region region = saveRegion();
		Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
				.name("행복약국2").region(region).lat(37.5).lng(127.0).build());
		Drug drug = drugRepository.save(Drug.builder()
				.name("타이레놀정500밀리그람").displayName("타이레놀 500mg")
				.category("해열진통").packageUnit("8정").build());

		PriceReport report = priceReportRepository.save(PriceReport.builder()
				.pharmacy(pharmacy).drug(drug).price(2800).purchasedAt(LocalDate.now()).build());
		Long id = report.getId();
		em.flush();
		em.clear();

		PriceReport found = priceReportRepository.findById(id).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(ReportStatus.ACTIVE);

		String rawStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM price_report WHERE id = ?", String.class, id);
		assertThat(rawStatus).isEqualTo("ACTIVE");
	}

	@Test
	void pharmacyDrugPriceStat_저장_조회() {
		Region region = saveRegion();
		Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
				.name("행복약국3").region(region).lat(37.5).lng(127.0).build());
		Drug drug = drugRepository.save(Drug.builder()
				.name("타이레놀정500밀리그람").displayName("타이레놀 500mg")
				.category("해열진통").packageUnit("8정").build());

		PharmacyDrugPriceStat stat = priceStatRepository.save(PharmacyDrugPriceStat.builder()
				.pharmacy(pharmacy).drug(drug)
				.repPrice(2800).minPrice(2500).maxPrice(3000).avgPrice(2800)
				.reportCount(3).lastReportedAt(LocalDate.now())
				.windowDays((short) 90).calculatedAt(Instant.now())
				.build());
		Long id = stat.getId();
		em.flush();
		em.clear();

		PharmacyDrugPriceStat found = priceStatRepository.findById(id).orElseThrow();
		assertThat(found.getRepPrice()).isEqualTo(2800);
		assertThat(found.getWindowDays()).isEqualTo((short) 90);
	}
}
