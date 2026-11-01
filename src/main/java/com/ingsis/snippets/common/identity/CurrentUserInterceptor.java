package com.ingsis.snippets.common.identity;

import com.ingsis.snippets.common.error.UnauthenticatedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the {@link CurrentUser} of every request and rejects the ones without it (401).
 * This is the only class that reads the identity header (D02).
 */
class CurrentUserInterceptor implements HandlerInterceptor {

	static final String USER_HEADER = "X-User-Id";
	static final String ATTRIBUTE = CurrentUser.class.getName();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String userId = request.getHeader(USER_HEADER);
		if (userId == null || userId.isBlank()) {
			throw new UnauthenticatedException("Missing " + USER_HEADER + " header");
		}
		request.setAttribute(ATTRIBUTE, new CurrentUser(userId.strip()));
		return true;
	}

}
