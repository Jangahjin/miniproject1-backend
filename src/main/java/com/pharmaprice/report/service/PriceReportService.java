package com.pharmaprice.report.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.exception.DrugNotFoundException;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.exception.PharmacyNotFoundException;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.service.PriceStatService;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.PriceReportRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.exception.DrugNotOtcException;
import com.pharmaprice.report.exception.DuplicateReportException;
import com.pharmaprice.report.exception.InvalidDateRangeException;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.report.repository.UploadedFileRepository;

/** 가격 제보 생성 (docs/ROADMAP.md T-26, docs/API.md §6). */
@Service
public class PriceReportService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_PAST_DAYS = 180;
	private static final double OUTLIER_LOW_RATIO = 0.3;
	private static final double OUTLIER_HIGH_RATIO = 3.0;
	private static final String DUPLICATE_CONSTRAINT_NAME = "uq_report_user_pair_day";

	private final PriceReportRepository priceReportRepository;
	private final PharmacyRepository pharmacyRepository;
	private final DrugRepository drugRepository;
	private final AppUserRepository appUserRepository;
	private final UploadedFileRepository uploadedFileRepository;
	private final PriceStatService priceStatService;

	public PriceReportService(
			PriceReportRepository priceReportRepository,
			PharmacyRepository pharmacyRepository,
			DrugRepository drugRepository,
			AppUserRepository appUserRepository,
			UploadedFileRepository uploadedFileRepository,
			PriceStatService priceStatService) {
		this.priceReportRepository = priceReportRepository;
		this.pharmacyRepository = pharmacyRepository;
		this.drugRepository = drugRepository;
		this.appUserRepository = appUserRepository;
		this.uploadedFileRepository = uploadedFileRepository;
		this.priceStatService = priceStatService;
	}

	@Transactional
	public PriceReportResponse create(long userId, PriceReportRequest request) {
		Pharmacy pharmacy = pharmacyRepository.findById(request.pharmacyId())
				.filter(Pharmacy::isActive)
				.orElseThrow(() -> new PharmacyNotFoundException(request.pharmacyId()));
		Drug drug = drugRepository.findById(request.drugId())
				.orElseThrow(() -> new DrugNotFoundException(request.drugId()));
		if (!drug.isOtcFlag()) {
			throw new DrugNotOtcException(request.drugId());
		}

		LocalDate purchasedAt = request.purchasedAt() != null ? request.purchasedAt() : LocalDate.now(KST);
		validateDateRange(purchasedAt);

		AppUser user = appUserRepository.findById(userId).orElseThrow();
		UploadedFile receiptFile = request.receiptFileId() != null
				? uploadedFileRepository.getReferenceById(request.receiptFileId())
				: null;

		Outlier outlier = detectOutlier(request.drugId(), request.price());

		PriceReport report = PriceReport.builder()
				.pharmacy(pharmacy)
				.drug(drug)
				.user(user)
				.price(request.price())
				.purchasedAt(purchasedAt)
				.source(ReportSource.FORM)
				.flagged(outlier.flagged())
				.flagReason(outlier.reason())
				.receiptFile(receiptFile)
				.memo(request.memo())
				.build();

		try {
			priceReportRepository.saveAndFlush(report);
		} catch (DataIntegrityViolationException e) {
			if (isDuplicateReportViolation(e)) {
				throw new DuplicateReportException(request.pharmacyId(), request.drugId());
			}
			throw e;
		}

		// PriceStatService.recalculate()는 native SQL upsert 직후 entityManager.clear()를
		// 호출한다(캐시된 stat을 다시 읽기 위함). clear()는 flush 없이 영속성 컨텍스트를 통째로
		// 비우므로, 그 전에 반드시 saveAndFlush로 report_count 증가분을 먼저 DB에 반영해야 한다.
		user.incrementReportCount();
		appUserRepository.saveAndFlush(user);

		PharmacyDrugPriceStat stat =
				priceStatService.recalculate(request.pharmacyId(), request.drugId()).orElse(null);

		return PriceReportResponse.of(report, stat, outlier.warning());
	}

	private static void validateDateRange(LocalDate purchasedAt) {
		LocalDate today = LocalDate.now(KST);
		if (purchasedAt.isAfter(today)) {
			throw new InvalidDateRangeException("구매일은 미래일 수 없습니다.");
		}
		if (purchasedAt.isBefore(today.minusDays(MAX_PAST_DAYS))) {
			throw new InvalidDateRangeException("구매일은 %d일 이전일 수 없습니다.".formatted(MAX_PAST_DAYS));
		}
	}

	private Outlier detectOutlier(long drugId, int price) {
		Double median = priceReportRepository.findMedianPriceByDrugId(drugId);
		if (median == null) {
			return Outlier.none();
		}
		long low = Math.round(median * OUTLIER_LOW_RATIO);
		long high = Math.round(median * OUTLIER_HIGH_RATIO);
		if (price < low) {
			return Outlier.flagged(FlagReason.OUTLIER_LOW, low, high);
		}
		if (price > high) {
			return Outlier.flagged(FlagReason.OUTLIER_HIGH, low, high);
		}
		return Outlier.none();
	}

	private static boolean isDuplicateReportViolation(DataIntegrityViolationException e) {
		Throwable cause = e.getMostSpecificCause();
		return cause.getMessage() != null && cause.getMessage().contains(DUPLICATE_CONSTRAINT_NAME);
	}

	private record Outlier(boolean flagged, FlagReason reason, String warning) {

		static Outlier none() {
			return new Outlier(false, null, null);
		}

		static Outlier flagged(FlagReason reason, long low, long high) {
			String warning = String.format(
					Locale.US,
					"입력하신 가격이 이 약품의 일반적인 가격대(%,d~%,d원)와 크게 달라 통계에 반영되지 않았습니다. 관리자 확인 후 반영됩니다.",
					low, high);
			return new Outlier(true, reason, warning);
		}
	}
}
