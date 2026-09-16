package com.pharmaprice.report.domain;

import java.time.LocalDate;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.common.domain.BaseTimeEntity;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.pharmacy.domain.Pharmacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "price_report")
@Getter
@NoArgsConstructor
@SuperBuilder
public class PriceReport extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pharmacy_id", nullable = false)
	private Pharmacy pharmacy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "drug_id", nullable = false)
	private Drug drug;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@Column(nullable = false)
	private int price;

	@Column(name = "purchased_at", nullable = false)
	private LocalDate purchasedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private ReportSource source = ReportSource.FORM;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private ReportStatus status = ReportStatus.ACTIVE;

	@Column(nullable = false)
	@Builder.Default
	private boolean flagged = false;

	@Enumerated(EnumType.STRING)
	@Column(name = "flag_reason", length = 100)
	private FlagReason flagReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receipt_file_id")
	private UploadedFile receiptFile;

	@Column(length = 200)
	private String memo;

	/** 관리자 제보 관리(docs/ROADMAP.md T-32)에서만 호출한다. */
	public void changeStatus(ReportStatus status) {
		this.status = status;
	}

	/**
	 * flagged 해제 시 flagReason도 함께 지운다 — flagReason은 "왜 이상치로 표시됐는지"를
	 * 나타내는 값이라 더 이상 이상치가 아니면 의미가 없다. 반대로 관리자가 수동으로
	 * flagged=true를 걸었는데 flagReason이 비어 있으면 MANUAL로 채운다.
	 */
	public void changeFlagged(boolean flagged) {
		this.flagged = flagged;
		if (!flagged) {
			this.flagReason = null;
		} else if (this.flagReason == null) {
			this.flagReason = FlagReason.MANUAL;
		}
	}
}
