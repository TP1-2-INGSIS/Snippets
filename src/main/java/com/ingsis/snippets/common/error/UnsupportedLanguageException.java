package com.ingsis.snippets.common.error;

public class UnsupportedLanguageException extends ApiException {

  public UnsupportedLanguageException(String message) {
    super(ErrorCode.UNSUPPORTED_LANGUAGE, message);
  }
}
