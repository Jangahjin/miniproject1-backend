package com.pharmaprice.report.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.pharmaprice.report.domain.UploadedFile;

/** POST /api/v1/uploads 응답 (docs/API.md §6). */
public record UploadResponse(
		long id, String originalName, String contentType, long sizeBytes, String url, OffsetDateTime createdAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static UploadResponse from(UploadedFile file) {
		return new UploadResponse(
				file.getId(), file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
				"/api/v1/uploads/" + file.getId(), file.getCreatedAt().atZone(KST).toOffsetDateTime());
	}
}
