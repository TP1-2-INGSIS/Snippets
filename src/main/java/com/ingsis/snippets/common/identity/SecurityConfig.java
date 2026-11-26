package com.ingsis.snippets.common.identity;

import com.ingsis.snippets.common.error.ApiError;
import com.ingsis.snippets.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Identity (D02). Every request needs an identity, except the error path and the health endpoints.
 *
 * <ul>
 *   <li>Auth0 access tokens ({@code Authorization: Bearer}) are validated as an OAuth2 resource
 *       server: signature (JWKS), issuer, audience and expiry. The {@link CurrentUser} is the
 *       {@code sub}.
 *   <li>With {@code AUTH_HEADER_IDENTITY_ENABLED=true}, a request without a Bearer token may use
 *       {@code X-User-Id} (tests, local, e2e only).
 *   <li>If neither is configured the application refuses to start.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  static final String USER_HEADER = "X-User-Id";

  private final String issuerUri;
  private final String audience;
  private final String jwksUri;
  private final boolean headerIdentityEnabled;

  SecurityConfig(
      @Value("${auth.issuer-uri:}") String issuerUri,
      @Value("${auth.audience:}") String audience,
      @Value("${auth.jwks-uri:}") String jwksUri,
      @Value("${auth.header-identity-enabled:false}") boolean headerIdentityEnabled) {
    this.issuerUri = issuerUri == null ? "" : issuerUri.strip();
    this.audience = audience == null ? "" : audience.strip();
    this.jwksUri = jwksUri == null ? "" : jwksUri.strip();
    this.headerIdentityEnabled = headerIdentityEnabled;
    validateConfiguration();
  }

  private boolean jwtConfigured() {
    return !issuerUri.isEmpty() && !audience.isEmpty();
  }

  private void validateConfiguration() {
    if (issuerUri.isEmpty() != audience.isEmpty()) {
      throw new IllegalStateException(
          "AUTH0_ISSUER_URI and AUTH0_AUDIENCE must be set together (or neither)");
    }
    if (!jwtConfigured() && !headerIdentityEnabled) {
      throw new IllegalStateException(
          "No identity source configured: set AUTH0_ISSUER_URI and AUTH0_AUDIENCE to validate"
              + " Auth0 tokens, or AUTH_HEADER_IDENTITY_ENABLED=true to accept X-User-Id"
              + " (tests/local only)");
    }
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    AuthenticationEntryPoint entryPoint = entryPoint(objectMapper);
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers("/error", "/actuator/health", "/actuator/health/**")
                .permitAll()
                .anyRequest()
                .authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));
    if (jwtConfigured()) {
      http.oauth2ResourceServer(
          o -> o.authenticationEntryPoint(entryPoint).jwt(Customizer.withDefaults()));
    }
    if (headerIdentityEnabled) {
      http.addFilterBefore(new HeaderIdentityFilter(), BearerTokenAuthenticationFilter.class);
    }
    return http.build();
  }

  /**
   * Built from the JWKS URI, so nothing is fetched until the first token arrives: the app starts
   * without reaching Auth0.
   */
  @Bean
  JwtDecoder jwtDecoder() {
    if (!jwtConfigured()) {
      // Header identity only: no token is ever accepted.
      return token -> {
        throw new org.springframework.security.oauth2.jwt.BadJwtException(
            "Token validation is not configured");
      };
    }
    String uri =
        jwksUri.isEmpty() ? withTrailingSlash(issuerUri) + ".well-known/jwks.json" : jwksUri;
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(uri).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator(audience)));
    return decoder;
  }

  private static String withTrailingSlash(String uri) {
    return uri.endsWith("/") ? uri : uri + "/";
  }

  static OAuth2TokenValidator<Jwt> audienceValidator(String expected) {
    return jwt -> {
      List<String> audiences = jwt.getAudience();
      if (audiences != null && audiences.contains(expected)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "The required audience is missing", null));
    };
  }

  /** 401 with the common error body (D03). */
  private static AuthenticationEntryPoint entryPoint(ObjectMapper objectMapper) {
    return (request, response, ex) -> {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      String message =
          request.getHeader(HttpHeaders.AUTHORIZATION) == null
              ? "Authentication required"
              : "Invalid or expired token";
      objectMapper.writeValue(
          response.getOutputStream(), ApiError.of(ErrorCode.UNAUTHENTICATED, message));
    };
  }

  /** Authenticates {@code X-User-Id} for requests that carry no Authorization header. */
  static final class HeaderIdentityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      String userId = request.getHeader(USER_HEADER);
      if (request.getHeader(HttpHeaders.AUTHORIZATION) == null
          && userId != null
          && !userId.isBlank()) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    userId.strip(), null, AuthorityUtils.NO_AUTHORITIES));
      }
      chain.doFilter(request, response);
    }
  }
}
