package com.ingsis.snippets.run;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Registers {@code /snippets/{id}/run}. Identity was already resolved by Spring Security. */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableConfigurationProperties(RunProperties.class)
class RunWebSocketConfig implements WebSocketConfigurer {

  static final String PATH = "/snippets/{id}/run";
  static final String ATTR_USER = "run.userId";
  static final String ATTR_SNIPPET = "run.snippetId";

  private static final Pattern PATH_REGEX = Pattern.compile("^/snippets/([^/]+)/run$");

  private final RunHandler handler;

  RunWebSocketConfig(RunHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Authentication is the token (not cookies), so any origin may open the socket.
    registry
        .addHandler(handler, PATH)
        .addInterceptors(new IdentityInterceptor())
        .setAllowedOrigins("*");
  }

  /** Tomcat's default text buffer is 8 KB: program output may be larger. */
  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> webSocketBuffers() {
    return factory ->
        factory.addContextCustomizers(context ->
            context.addParameter(
                "org.apache.tomcat.websocket.textBufferSize",
                String.valueOf(RunHandler.MAX_MESSAGE_BYTES)));
  }

  /** Copies the authenticated user and the snippet id into the session attributes. */
  static final class IdentityInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes) {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null
          && !(auth instanceof AnonymousAuthenticationToken)
          && auth.isAuthenticated()
          && auth.getName() != null
          && !auth.getName().isBlank()) {
        attributes.put(ATTR_USER, auth.getName());
      }
      Matcher matcher = PATH_REGEX.matcher(request.getURI().getPath());
      if (matcher.matches()) {
        try {
          attributes.put(ATTR_SNIPPET, UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException e) {
          // The handler closes with 1008 when the snippet id is missing.
        }
      }
      return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception) {
      // Nothing to do.
    }
  }
}
