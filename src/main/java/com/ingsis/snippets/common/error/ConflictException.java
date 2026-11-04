package com.ingsis.snippets.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

  /**
   * @param code a 409 code, e.g. {@link ErrorCode#ALREADY_SHARED}
   */
  public ConflictException(ErrorCode code, String message) {
    super(requireConflict(code), message);
  }

  private static ErrorCode requireConflict(ErrorCode code) {
    if (code.status() != HttpStatus.CONFLICT) {
      throw new IllegalArgumentException(code + " is not a 409 error code");
    }
    return code;
  }
}
