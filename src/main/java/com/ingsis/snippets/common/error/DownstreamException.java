package com.ingsis.snippets.common.error;

import java.util.List;

public class DownstreamException extends ApiException {

  public DownstreamException(String message, Throwable cause) {
    super(ErrorCode.DOWNSTREAM_ERROR, message, List.of(), cause);
  }
}
