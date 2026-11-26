package com.ingsis.snippets.common.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Auth0 token validation against a local RSA key served as a JWK set by WireMock. */
@WebMvcTest(
    controllers = IdentityProbeController.class,
    properties = "auth.header-identity-enabled=false")
class JwtValidationTest {

  private static final String ISSUER = "https://tenant.example.auth0.com/";
  private static final String AUDIENCE = "https://snippet-searcher/api";

  private static WireMockServer jwks;
  private static RSAKey signingKey;
  private static RSAKey otherKey;

  @Autowired private MockMvc mvc;

  @BeforeAll
  static void startJwks() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    jwks = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    jwks.start();
    jwks.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/jwks.json"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(signingKey.toPublicJWK()).toString())));
  }

  @AfterAll
  static void stopJwks() {
    jwks.stop();
  }

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("auth.issuer-uri", () -> ISSUER);
    registry.add("auth.audience", () -> AUDIENCE);
    registry.add("auth.jwks-uri", () -> "http://localhost:" + jwks.port() + "/jwks.json");
  }

  private static String token(RSAKey key, String issuer, String audience, long expiresInMs)
      throws Exception {
    Date now = new Date();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("auth0|usr_12345")
            .issuer(issuer)
            .audience(audience)
            .issueTime(new Date(now.getTime() - 3_600_000))
            .expirationTime(new Date(now.getTime() + expiresInMs))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  @Test
  void validTokenIdentifiesTheUserBySub() throws Exception {
    String token = token(signingKey, ISSUER, AUDIENCE, 600_000);
    mvc.perform(get("/probe/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("auth0|usr_12345"));
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() throws Exception {
    assertUnauthorized(token(otherKey, ISSUER, AUDIENCE, 600_000));
  }

  @Test
  void expiredTokenIsRejected() throws Exception {
    assertUnauthorized(token(signingKey, ISSUER, AUDIENCE, -600_000));
  }

  @Test
  void wrongAudienceIsRejected() throws Exception {
    assertUnauthorized(token(signingKey, ISSUER, "https://other/api", 600_000));
  }

  @Test
  void wrongIssuerIsRejected() throws Exception {
    assertUnauthorized(token(signingKey, "https://evil.example.com/", AUDIENCE, 600_000));
  }

  @Test
  void garbageTokenIsRejected() throws Exception {
    assertUnauthorized("not-a-jwt");
  }

  @Test
  void headerIsIgnoredWhenTheFlagIsOff() throws Exception {
    mvc.perform(get("/probe/me").header("X-User-Id", "user-ana"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
  }

  @Test
  void missingCredentialsAreRejected() throws Exception {
    mvc.perform(get("/probe/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.details").isArray());
  }

  private void assertUnauthorized(String token) throws Exception {
    mvc.perform(get("/probe/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Invalid or expired token"));
  }
}
