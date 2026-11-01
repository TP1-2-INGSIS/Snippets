package com.ingsis.snippets.common.error;

import java.util.List;

/** Base for domain exceptions; {@link GlobalExceptionHandler} turns them into an {@link ApiError}. */
public abstract class ApiException extends RuntimeException {

	private final ErrorCode code;
	private final List<?> details;

	protected ApiException(ErrorCode code, String message) {
		this(code, message, List.of(), null);
	}

	protected ApiException(ErrorCode code, String message, List<?> details, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.details = details == null ? List.of() : List.copyOf(details);
	}

	public ErrorCode code() {
		return code;
	}

	public List<?> details() {
		return details;
	}

}
