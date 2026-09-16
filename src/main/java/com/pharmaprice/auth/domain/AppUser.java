package com.pharmaprice.auth.domain;

import com.pharmaprice.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor
@SuperBuilder
public class AppUser extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 30)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private UserRole role = UserRole.USER;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "report_count", nullable = false)
	@Builder.Default
	private int reportCount = 0;

	/** 가격 제보 저장과 같은 트랜잭션에서 호출한다 (docs/ROADMAP.md T-26). */
	public void incrementReportCount() {
		this.reportCount++;
	}

	/** 관리자가 제보를 REJECTED로 바꿀 때 호출한다 (docs/ROADMAP.md T-32). */
	public void decrementReportCount() {
		this.reportCount = Math.max(0, this.reportCount - 1);
	}
}
