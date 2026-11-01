package com.ingsis.snippets.common.error;

public class ForbiddenException extends ApiException {

	public ForbiddenException(String message) {
		super(ErrorCode.FORBIDDEN, message);
	}

}
