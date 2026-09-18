package com.pharmaprice.report.controller;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.UploadResponse;
import com.pharmaprice.report.exception.FileTooLargeException;
import com.pharmaprice.report.exception.UnsupportedFileTypeException;
import com.pharmaprice.report.exception.UploadedFileNotFoundException;
import com.pharmaprice.report.repository.UploadedFileRepository;
import com.pharmaprice.report.service.FileStorageService;
import com.pharmaprice.report.service.ImageMagicBytes;

/** 영수증 이미지 업로드 · 조회 (docs/API.md §6, docs/ROADMAP.md T-27). OCR은 하지 않는다. */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

	private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

	private final FileStorageService fileStorageService;
	private final UploadedFileRepository uploadedFileRepository;
	private final AppUserRepository appUserRepository;

	public UploadController(
			FileStorageService fileStorageService,
			UploadedFileRepository uploadedFileRepository,
			AppUserRepository appUserRepository) {
		this.fileStorageService = fileStorageService;
		this.uploadedFileRepository = uploadedFileRepository;
		this.appUserRepository = appUserRepository;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public UploadResponse upload(
			@AuthenticationPrincipal Long userId,
			@RequestParam("file") MultipartFile file,
			@RequestParam("purpose") String purpose)
			throws IOException {
		// spring.servlet.multipart.max-file-size가 같은 5MB로 걸려 있어 이보다 큰 파일은
		// 보통 여기 도달하기 전에 MaxUploadSizeExceededException으로 걸러진다 — 이 체크는
		// 설정이 달라지는 환경을 대비한 이중 방어다.
		if (file.getSize() > MAX_SIZE_BYTES) {
			throw new FileTooLargeException();
		}

		byte[] content = file.getBytes();
		String contentType = ImageMagicBytes.detect(content).orElseThrow(UnsupportedFileTypeException::new);

		FileStorageService.StoredFile stored = fileStorageService.store(content, contentType);
		UploadedFile saved = uploadedFileRepository.save(UploadedFile.builder()
				.originalName(file.getOriginalFilename())
				.storedPath(stored.storedPath())
				.contentType(contentType)
				.sizeBytes(stored.sizeBytes())
				.uploadedBy(appUserRepository.getReferenceById(userId))
				.build());

		return UploadResponse.from(saved);
	}

	@GetMapping("/{fileId}")
	public ResponseEntity<Resource> download(Authentication authentication, @PathVariable long fileId)
			throws IOException {
		UploadedFile file =
				uploadedFileRepository.findById(fileId).orElseThrow(() -> new UploadedFileNotFoundException(fileId));

		long currentUserId = (Long) authentication.getPrincipal();
		boolean isOwner = file.getUploadedBy() != null && file.getUploadedBy().getId() == currentUserId;
		boolean isAdmin = authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
		if (!isOwner && !isAdmin) {
			throw new AccessDeniedException("본인이 업로드한 파일만 조회할 수 있습니다.");
		}

		Resource resource = fileStorageService.load(file.getStoredPath());
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.getContentType())).body(resource);
	}
}
