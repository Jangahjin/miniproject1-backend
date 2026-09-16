package com.pharmaprice.report.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.pharmaprice.AbstractIntegrationTest;

/** docs/ROADMAP.md T-27 완료 판정. */
@AutoConfigureMockMvc
class UploadControllerTest extends AbstractIntegrationTest {

	private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};

	@Autowired
	MockMvc mockMvc;

	String accessToken;
	String otherAccessToken;

	@BeforeEach
	void setUp() throws Exception {
		accessToken = signupAndLogin();
		otherAccessToken = signupAndLogin();
	}

	private String signupAndLogin() throws Exception {
		String email = "upload-test-" + System.nanoTime() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!","nickname":"업로더"}
								""".formatted(email)))
				.andExpect(status().isCreated());

		String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password123!"}
								""".formatted(email)))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(loginResponse, "$.accessToken");
	}

	@Test
	void 정상_업로드하면_201이고_업로더_본인은_다운로드할_수_있다() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

		String uploadResponse = mockMvc.perform(multipart("/api/v1/uploads")
						.file(file)
						.param("purpose", "RECEIPT")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType").value("image/jpeg"))
				.andExpect(jsonPath("$.url").exists())
				.andReturn().getResponse().getContentAsString();

		long fileId = ((Number) JsonPath.read(uploadResponse, "$.id")).longValue();

		mockMvc.perform(get("/api/v1/uploads/{fileId}", fileId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());
	}

	@Test
	void 확장자를_jpg로_위장한_PDF는_415를_반환한다() throws Exception {
		byte[] fakePdf = "%PDF-1.4 이건 사실 PDF입니다".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", fakePdf);

		mockMvc.perform(multipart("/api/v1/uploads")
						.file(file)
						.param("purpose", "RECEIPT")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void 오메가바이트_초과_파일은_413을_반환한다() throws Exception {
		byte[] tooLarge = new byte[6 * 1024 * 1024];
		System.arraycopy(JPEG_BYTES, 0, tooLarge, 0, JPEG_BYTES.length);
		MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooLarge);

		mockMvc.perform(multipart("/api/v1/uploads")
						.file(file)
						.param("purpose", "RECEIPT")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isPayloadTooLarge());
	}

	@Test
	void 타인의_파일을_조회하면_403을_반환한다() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);
		String uploadResponse = mockMvc.perform(multipart("/api/v1/uploads")
						.file(file)
						.param("purpose", "RECEIPT")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andReturn().getResponse().getContentAsString();
		long fileId = ((Number) JsonPath.read(uploadResponse, "$.id")).longValue();

		mockMvc.perform(get("/api/v1/uploads/{fileId}", fileId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAccessToken))
				.andExpect(status().isForbidden());
	}
}
