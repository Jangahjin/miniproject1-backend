package com.pharmaprice.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import com.pharmaprice.auth.exception.EmailAlreadyExistsException;
import com.pharmaprice.common.dto.ErrorResponse;
import com.pharmaprice.drug.exception.DrugNotFoundException;
import com.pharmaprice.pharmacy.exception.PharmacyNotFoundException;
import com.pharmaprice.report.exception.DrugNotOtcException;
import com.pharmaprice.report.exception.DuplicateReportException;
import com.pharmaprice.report.exception.FileTooLargeException;
import com.pharmaprice.report.exception.InvalidDateRangeException;
import com.pharmaprice.report.exception.PriceReportNotFoundException;
import com.pharmaprice.report.exception.UnsupportedFileTypeException;
import com.pharmaprice.report.exception.UploadedFileNotFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * 모든 예외를 docs/API.md §1.2 포맷으로 통일한다 (docs/ROADMAP.md T-35). 401/403은
 * Spring Security 필터 체인(T-23, SecurityResponseWriter)이 DispatcherServlet보다
 * 먼저 처리하므로 여기서 다루지 않는다 — {@code AccessDeniedException} 핸들러를
 * 추가하면 그 경로를 가로채 버린다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// AccessDeniedException/AuthenticationException(BadCredentialsException,
	// InsufficientAuthenticationException 등)은 여기서 처리하지 않고 다시 던진다.
	// @ExceptionHandler(Exception.class)가 있으면 이 둘도 가장 구체적인 매치로
	// 잡혀버려 Spring Security의 ExceptionTranslationFilter(JwtAccessDeniedHandler/
	// JwtAuthenticationEntryPoint, T-23)까지 전파되지 못하고 500으로 둔갑한다.
	// ExceptionHandlerExceptionResolver는 핸들러 메서드가 예외를 던지면 처리를
	// 포기하고 원래 예외를 그대로 흘려보내므로, 이 rethrow가 Security 레이어까지
	// 전파를 보장하는 표준적인 해법이다.
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	public void rethrowToSecurityLayer(RuntimeException ex) throws RuntimeException {
		throw ex;
	}

	// --- 검증 실패 ---

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldError)
				.toList();
		log.warn("검증 실패: {}", fieldErrors);
		return respond(ErrorCode.VALIDATION_FAILED, firstMessageOrDefault(fieldErrors), fieldErrors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
				.map(this::toFieldError)
				.toList();
		log.warn("검증 실패: {}", fieldErrors);
		return respond(ErrorCode.VALIDATION_FAILED, firstMessageOrDefault(fieldErrors), fieldErrors);
	}

	// SearchService/PharmacyController의 위치·regionCode 조합 검증 등, 전용 코드가
	// 없는 400 케이스가 여기로 들어온다 (이 앱에서 ResponseStatusException은 전부 400).
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
		log.warn("잘못된 요청: {}", ex.getReason());
		return ResponseEntity.status(ex.getStatusCode())
				.body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED.name(), ex.getReason()));
	}

	@ExceptionHandler(InvalidRadiusException.class)
	public ResponseEntity<ErrorResponse> handleInvalidRadius(InvalidRadiusException ex) {
		return respond(ErrorCode.INVALID_RADIUS, ex.getMessage());
	}

	@ExceptionHandler(InvalidCoordinateException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCoordinate(InvalidCoordinateException ex) {
		return respond(ErrorCode.INVALID_COORDINATE, ex.getMessage());
	}

	@ExceptionHandler(InvalidDateRangeException.class)
	public ResponseEntity<ErrorResponse> handleInvalidDateRange(InvalidDateRangeException ex) {
		return respond(ErrorCode.INVALID_DATE_RANGE, ex.getMessage());
	}

	// --- 리소스 없음 ---

	@ExceptionHandler(PharmacyNotFoundException.class)
	public ResponseEntity<ErrorResponse> handlePharmacyNotFound(PharmacyNotFoundException ex) {
		return respond(ErrorCode.PHARMACY_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(DrugNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleDrugNotFound(DrugNotFoundException ex) {
		return respond(ErrorCode.DRUG_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler({ PriceReportNotFoundException.class, com.pharmaprice.admin.exception.PriceReportNotFoundException.class })
	public ResponseEntity<ErrorResponse> handleReportNotFound(RuntimeException ex) {
		return respond(ErrorCode.REPORT_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(UploadedFileNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUploadedFileNotFound(UploadedFileNotFoundException ex) {
		return respond(ErrorCode.FILE_NOT_FOUND, ex.getMessage());
	}

	// --- 충돌 ---

	@ExceptionHandler(EmailAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
		return respond(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getMessage());
	}

	// ex.getMessage()에는 pharmacyId/drugId 같은 내부 식별자가 섞여 있어 화면에 그대로
	// 보여주지 않는다 — ErrorCode의 정제된 기본 메시지를 쓴다.
	@ExceptionHandler(DuplicateReportException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateReport(DuplicateReportException ex) {
		return respond(ErrorCode.DUPLICATE_REPORT, ErrorCode.DUPLICATE_REPORT.message());
	}

	// 앱 레벨에서 미리 걸러내지 못한 유니크 제약 위반에 대한 방어용 폴백. SQL/제약조건명이
	// 노출되지 않게 일반 메시지만 응답하고, 원인은 로그에만 남긴다.
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.error("데이터 무결성 제약 위반", ex);
		return respond(ErrorCode.DATA_CONFLICT, ErrorCode.DATA_CONFLICT.message());
	}

	// --- 그 외 ---

	@ExceptionHandler(DrugNotOtcException.class)
	public ResponseEntity<ErrorResponse> handleDrugNotOtc(DrugNotOtcException ex) {
		return respond(ErrorCode.DRUG_NOT_OTC, ex.getMessage());
	}

	@ExceptionHandler(FileTooLargeException.class)
	public ResponseEntity<ErrorResponse> handleFileTooLarge(FileTooLargeException ex) {
		return respond(ErrorCode.FILE_TOO_LARGE, ex.getMessage());
	}

	// spring.servlet.multipart.max-file-size 설정에 걸려 컨트롤러에 도달하기 전에
	// 던져지는 경우 (docs/ROADMAP.md T-27의 이중 방어 중 1차 방어선).
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
		return respond(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.message());
	}

	@ExceptionHandler(UnsupportedFileTypeException.class)
	public ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
		return respond(ErrorCode.UNSUPPORTED_FILE_TYPE, ex.getMessage());
	}

	// 예상하지 못한 모든 예외의 최종 방어선. 스택트레이스는 로그에만 남기고 응답에는
	// 절대 포함하지 않는다.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("처리되지 않은 예외", ex);
		return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
	}

	private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
		return ResponseEntity.status(code.httpStatus()).body(ErrorResponse.of(code.name(), message));
	}

	private ResponseEntity<ErrorResponse> respond(
			ErrorCode code, String message, List<ErrorResponse.FieldError> fieldErrors) {
		return ResponseEntity.status(code.httpStatus()).body(ErrorResponse.of(code.name(), message, fieldErrors));
	}

	private ErrorResponse.FieldError toFieldError(FieldError fieldError) {
		return new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
	}

	private ErrorResponse.FieldError toFieldError(ConstraintViolation<?> violation) {
		String path = violation.getPropertyPath().toString();
		String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
		return new ErrorResponse.FieldError(field, violation.getMessage());
	}

	private String firstMessageOrDefault(List<ErrorResponse.FieldError> fieldErrors) {
		return fieldErrors.isEmpty() ? ErrorCode.VALIDATION_FAILED.message() : fieldErrors.get(0).reason();
	}
}
