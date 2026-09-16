package com.pharmaprice.report.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

/**
 * {@code ${app.upload-dir}/{yyyy}/{MM}/{uuid}.{ext}} 경로에 저장한다
 * (docs/ROADMAP.md T-27). Docker 볼륨이 없으므로 경로는 설정으로 빼고
 * `.gitignore`에 넣는다. 원본 파일명은 경로에 절대 쓰지 않는다(경로 조작 방지).
 */
@Service
public class LocalFileStorageService implements FileStorageService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Map<String, String> EXTENSIONS =
			Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

	private final Path uploadRoot;

	public LocalFileStorageService(@Value("${app.upload-dir}") String uploadDir) {
		this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
	}

	@Override
	public StoredFile store(byte[] content, String contentType) throws IOException {
		LocalDate today = LocalDate.now(KST);
		String extension = EXTENSIONS.getOrDefault(contentType, "bin");
		String relativePath = "%04d/%02d/%s.%s"
				.formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);

		Path target = resolveWithinRoot(relativePath);
		Files.createDirectories(target.getParent());
		Files.write(target, content);

		return new StoredFile(relativePath, content.length);
	}

	@Override
	public Resource load(String storedPath) throws IOException {
		return new UrlResource(resolveWithinRoot(storedPath).toUri());
	}

	/** {@code storedPath}는 서버가 UUID로 생성하지만, 이중 방어로 루트 이탈 여부를 한 번 더 확인한다. */
	private Path resolveWithinRoot(String storedPath) {
		Path target = uploadRoot.resolve(storedPath).normalize();
		if (!target.startsWith(uploadRoot)) {
			throw new IllegalArgumentException("허용되지 않은 파일 경로입니다: " + storedPath);
		}
		return target;
	}
}
