package com.ingsis.snippets.common.error;

import java.util.List;

public class InvalidSnippetException extends ApiException {

  /**
   * @param errors source errors reported by Language Service ({rule, message, line, column})
   */
  public InvalidSnippetException(List<?> errors) {
    super(ErrorCode.INVALID_SNIPPET, "Snippet is not valid", errors, null);
  }
}
