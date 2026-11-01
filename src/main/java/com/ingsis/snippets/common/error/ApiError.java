package com.ingsis.snippets.common.error;

import java.util.List;

/** Body of every error response: {@code { "error": "CODE", "message": "...", "details": [] }} (D03). */
public record ApiError(String error, String message, List<?> details) {

	public ApiError {
		details = details == null ? List.of() : List.copyOf(details);
	}

	public static ApiError of(ErrorCode code, String message) {
		return new ApiError(code.name(), message, List.of());
	}

}
