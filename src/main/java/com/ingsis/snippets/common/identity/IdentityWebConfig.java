package com.ingsis.snippets.common.identity;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link CurrentUser} argument resolver and the security setup. Being a {@link
 * WebMvcConfigurer}, it is also picked up by {@code @WebMvcTest} slices, which then get the same
 * security chain as the real app.
 */
@Configuration(proxyBeanMethods = false)
@Import(SecurityConfig.class)
class IdentityWebConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentUserArgumentResolver());
  }
}
