package com.ingsis.snippets.common.identity;

import com.ingsis.snippets.common.error.UnauthenticatedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injects the authenticated {@link CurrentUser} into controller methods. */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return CurrentUser.class.equals(parameter.getParameterType());
  }

  @Override
  public CurrentUser resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || auth instanceof AnonymousAuthenticationToken
        || !auth.isAuthenticated()
        || auth.getName() == null
        || auth.getName().isBlank()) {
      throw new UnauthenticatedException("Authentication required");
    }
    return new CurrentUser(auth.getName());
  }
}
