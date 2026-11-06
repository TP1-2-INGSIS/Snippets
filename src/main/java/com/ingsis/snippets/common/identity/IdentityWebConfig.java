package com.ingsis.snippets.common.identity;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

/** Every endpoint requires a {@link CurrentUser}; Spring's own error path is left out. */
@Configuration(proxyBeanMethods = false)
class IdentityWebConfig implements WebMvcConfigurer {

  private final ObjectMapper objectMapper;

  IdentityWebConfig(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new CurrentUserInterceptor(new JwtTokenParser(objectMapper)))
        .excludePathPatterns("/error");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentUserArgumentResolver());
  }
}
