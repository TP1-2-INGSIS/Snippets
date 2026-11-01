package com.ingsis.snippets.common.error;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place where exceptions become HTTP responses (D03).
 * <ul>
 * <li>{@link ApiException}: status and code come from its {@link ErrorCode}.</li>
 * <li>Spring MVC exceptions (malformed body, missing params, unknown route, ...): keep
 * Spring's status, but use our body. Any 400 is reported as {@code BAD_REQUEST}.</li>
 * <li>Anything else: 500 {@code INTERNAL_ERROR}, without leaking the exception message.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApi(ApiException ex) {
		if (ex.code().status().is5xxServerError()) {
			log.warn("{}: {}", ex.code(), ex.getMessage(), ex);
		}
		return ResponseEntity.status(ex.code().status())
			.body(new ApiError(ex.code().name(), ex.getMessage(), ex.details()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.internalServerError()
			.body(ApiError.of(ErrorCode.INTERNAL_ERROR, "Unexpected error"));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> fields = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(GlobalExceptionHandler::toDetail)
			.toList();
		ApiError body = new ApiError(ErrorCode.BAD_REQUEST.name(), "Invalid request", fields);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		Object apiBody = body instanceof ApiError ? body
				: new ApiError(codeFor(statusCode), messageFor(ex, body), List.of());
		return super.handleExceptionInternal(ex, apiBody, headers, statusCode, request);
	}

	private static Map<String, String> toDetail(FieldError error) {
		String message = error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
		return Map.of("field", error.getField(), "message", message);
	}

	private static String codeFor(HttpStatusCode statusCode) {
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
			return ErrorCode.BAD_REQUEST.name();
		}
		HttpStatus status = HttpStatus.resolve(statusCode.value());
		return status == null ? String.valueOf(statusCode.value()) : status.name();
	}

	private static String messageFor(Exception ex, @Nullable Object body) {
		if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
			return problem.getDetail();
		}
		return ex.getMessage();
	}

}
