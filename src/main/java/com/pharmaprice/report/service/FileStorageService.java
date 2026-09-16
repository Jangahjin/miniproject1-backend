package com.pharmaprice.report.service;

import java.io.IOException;

import org.springframework.core.io.Resource;

/**
 * 파일 저장 추상화 (docs/ROADMAP.md T-27). 지금은 로컬 디스크
 * ({@link LocalFileStorageService})지만, S3 전환 시 구현체만 교체하면 되도록
 * 인터페이스로 분리해둔다.
 */
public interface FileStorageService {

	/** {@code content}를 저장하고, 이후 {@link #load}에 쓸 상대 경로를 돌려준다. */
	StoredFile store(byte[] content, String contentType) throws IOException;

	Resource load(String storedPath) throws IOException;

	record StoredFile(String storedPath, long sizeBytes) {
	}
}
