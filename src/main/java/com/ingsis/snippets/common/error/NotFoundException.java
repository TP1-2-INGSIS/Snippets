package com.ingsis.snippets.common.error;

public class NotFoundException extends ApiException {

	public NotFoundException(String message) {
		super(ErrorCode.NOT_FOUND, message);
	}

}
