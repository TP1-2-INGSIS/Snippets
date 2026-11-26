package com.ingsis.snippets.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;

class SecurityConfigTest {

  @Test
  void failsFastWhenNoIdentitySourceIsConfigured() {
    assertThatThrownBy(() -> new SecurityConfig("", "", "", false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUTH0_ISSUER_URI")
        .hasMessageContaining("AUTH_HEADER_IDENTITY_ENABLED");
  }

  @Test
  void failsFastWhenOnlyHalfOfTheAuth0ConfigIsSet() {
    assertThatThrownBy(() -> new SecurityConfig("https://t.auth0.com/", "", "", true))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void headerOnlyModeStartsAndNeverAcceptsTokens() {
    SecurityConfig config = new SecurityConfig("", "", "", true);
    assertThatThrownBy(() -> config.jwtDecoder().decode("a.b.c"))
        .isInstanceOf(BadJwtException.class);
  }

  @Test
  void jwtDecoderIsBuiltWithoutReachingAuth0() {
    SecurityConfig config = new SecurityConfig("https://unreachable.invalid/", "aud", "", false);
    assertThat(config.jwtDecoder()).isNotNull();
  }
}
