package com.ingsis.snippets.clients.language;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ingsis.snippets.clients.DownstreamCalls;
import com.ingsis.snippets.clients.DownstreamError;
import com.ingsis.snippets.common.error.ApiException;
import com.ingsis.snippets.common.error.BadRequestException;
import com.ingsis.snippets.common.error.InvalidSnippetException;
import com.ingsis.snippets.common.error.UnsupportedLanguageException;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

public class RestLanguageClient implements LanguageClient {

  private static final String SERVICE = "Language Service";

  private final RestClient client;

  public RestLanguageClient(RestClient client) {
    this.client = client;
  }

  @Override
  public List<LanguageInfo> languages() {
    return call(() ->
        client
            .get()
            .uri("/languages")
            .retrieve()
            .body(new ParameterizedTypeReference<List<LanguageInfo>>() {}));
  }

  @Override
  public ValidationResult validate(String language, String content, String version) {
    return post(
        language, "validate", new SourceRequest(content, version, null), ValidationResult.class);
  }

  @Override
  public String format(String language, String content, String version, @Nullable JsonNode rules) {
    return post(
            language, "format", new SourceRequest(content, version, rules), FormatResponse.class)
        .formatted();
  }

  @Override
  public List<SourceError> lint(
      String language, String content, String version, @Nullable JsonNode rules) {
    return post(language, "lint", new SourceRequest(content, version, rules), LintResponse.class)
        .violations();
  }

  @Override
  public TestResult test(
      String language,
      String content,
      String version,
      List<String> inputs,
      List<String> expectedOutputs) {
    return post(
        language,
        "test",
        new TestRequest(content, version, inputs, expectedOutputs),
        TestResult.class);
  }

  private <T> T post(String language, String operation, Object body, Class<T> responseType) {
    return call(() ->
        client
            .post()
            .uri("/languages/{lang}/{operation}", language, operation)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(responseType));
  }

  private static <T> T call(Supplier<T> request) {
    return DownstreamCalls.call(SERVICE, request, RestLanguageClient::translate);
  }

  private static @Nullable ApiException translate(DownstreamError error) {
    return switch (error.error()) {
      case "LANGUAGE_NOT_FOUND", "UNSUPPORTED_VERSION" ->
          new UnsupportedLanguageException(error.message());
      case "INVALID_RULES" -> new BadRequestException(error.message());
      case "INVALID_SOURCE" -> new InvalidSnippetException(error.details());
      default -> null;
    };
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record SourceRequest(String content, String version, @Nullable JsonNode rules) {}

  private record TestRequest(
      String content, String version, List<String> inputs, List<String> expectedOutputs) {}

  private record FormatResponse(String formatted) {}

  private record LintResponse(List<SourceError> violations) {}
}
