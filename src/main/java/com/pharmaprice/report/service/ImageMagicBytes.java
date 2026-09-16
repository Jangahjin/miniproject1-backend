package com.pharmaprice.report.service;

import java.util.Optional;

/**
 * 확장자나 클라이언트가 보낸 Content-Type이 아니라 파일의 실제 매직 바이트로
 * 이미지 종류를 판정한다 (docs/ROADMAP.md T-27). ".jpg"로 위장한 PDF 같은
 * 시도를 막는다.
 */
public final class ImageMagicBytes {

	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PNG = {
		(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

	private ImageMagicBytes() {
	}

	/** RIFF 컨테이너의 4~11바이트가 "WEBP"인지까지 확인해야 진짜 WebP다. */
	public static Optional<String> detect(byte[] content) {
		if (startsWith(content, JPEG)) {
			return Optional.of("image/jpeg");
		}
		if (startsWith(content, PNG)) {
			return Optional.of("image/png");
		}
		if (isWebp(content)) {
			return Optional.of("image/webp");
		}
		return Optional.empty();
	}

	private static boolean isWebp(byte[] content) {
		if (content.length < 12) {
			return false;
		}
		String riff = new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
		String webp = new String(content, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
		return "RIFF".equals(riff) && "WEBP".equals(webp);
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (content[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}
}
