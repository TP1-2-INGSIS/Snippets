package com.ingsis.snippets.clients;

import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.permission.PermissionClient;
import java.time.Duration;
import org.springframework.web.client.RestClient;

/** Builds the real clients, through their real configuration, against a local URL. */
public final class WireMockClients {

  private WireMockClients() {}

  public static LanguageClient language(String url, Duration readTimeout) {
    return new ClientsConfiguration()
        .languageClient(RestClient.builder(), properties(url, readTimeout));
  }

  public static PermissionClient permission(String url) {
    return new ClientsConfiguration()
        .permissionClient(RestClient.builder(), properties(url, Duration.ofSeconds(10)));
  }

  private static ServicesProperties properties(String url, Duration readTimeout) {
    ServicesProperties.Endpoint endpoint = new ServicesProperties.Endpoint(url);
    return new ServicesProperties(endpoint, endpoint, Duration.ofSeconds(1), readTimeout);
  }
}
