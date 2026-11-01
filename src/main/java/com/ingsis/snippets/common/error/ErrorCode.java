package com.ingsis.snippets.common.error;

import org.springframework.http.HttpStatus;

/** Domain error codes exposed in {@link ApiError#error()} (D03). */
public enum ErrorCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST),
	UNSUPPORTED_LANGUAGE(HttpStatus.BAD_REQUEST),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	ALREADY_SHARED(HttpStatus.CONFLICT),
	INVALID_SNIPPET(HttpStatus.UNPROCESSABLE_CONTENT),
	DOWNSTREAM_ERROR(HttpStatus.BAD_GATEWAY),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

}
