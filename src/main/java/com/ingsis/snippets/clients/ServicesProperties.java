package com.ingsis.snippets.clients;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the downstream services live and how long to wait for them ({@code services.*}). */
@ConfigurationProperties("services")
public record ServicesProperties(
    Endpoint language, Endpoint permission, Duration connectTimeout, Duration readTimeout) {

  public record Endpoint(String url) {}
}
