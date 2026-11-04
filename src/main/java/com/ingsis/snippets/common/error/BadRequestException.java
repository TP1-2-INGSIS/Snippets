package com.ingsis.snippets.common.error;

public class BadRequestException extends ApiException {

  public BadRequestException(String message) {
    super(ErrorCode.BAD_REQUEST, message);
  }
}
