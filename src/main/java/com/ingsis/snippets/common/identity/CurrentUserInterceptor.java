package com.ingsis.snippets.common.identity;

import com.ingsis.snippets.common.error.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves the {@link CurrentUser} of every request and rejects the ones without it (401). This is
 * the only class that reads the identity headers (D02).
 */
class CurrentUserInterceptor implements HandlerInterceptor {

  static final String USER_HEADER = "X-User-Id";
  static final String AUTH_HEADER = "Authorization";
  static final String BEARER_PREFIX = "Bearer ";
  static final String ATTRIBUTE = CurrentUser.class.getName();

  private final JwtTokenParser tokenParser;

  CurrentUserInterceptor() {
    this(new JwtTokenParser(new ObjectMapper()));
  }

  CurrentUserInterceptor(JwtTokenParser tokenParser) {
    this.tokenParser = tokenParser;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String authHeader = request.getHeader(AUTH_HEADER);
    if (authHeader != null
        && authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      String token = authHeader.substring(BEARER_PREFIX.length()).strip();
      String subject = tokenParser.extractSubject(token);
      request.setAttribute(ATTRIBUTE, new CurrentUser(subject));
      return true;
    }

    String userId = request.getHeader(USER_HEADER);
    if (userId != null && !userId.isBlank()) {
      request.setAttribute(ATTRIBUTE, new CurrentUser(userId.strip()));
      return true;
    }

    throw new UnauthenticatedException("Missing " + USER_HEADER + " header");
  }
}
