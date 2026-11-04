package com.ingsis.snippets.rules.service;

import com.ingsis.snippets.common.error.UnsupportedLanguageException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleConfig;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.repository.RuleConfigRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reglas por (usuario, lenguaje, tipo) con defaults (D07). El default vive en {@code
 * resources/rules/{language}-{kind}.json} y no se persiste hasta el primer {@code save}. Un
 * lenguaje sin archivo de defaults no está soportado.
 */
@Service
public class RulesService {

  private final RuleConfigRepository repository;

  private final ObjectMapper objectMapper;

  public RulesService(RuleConfigRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /** La regla guardada del usuario, o el default del servicio si no tiene ninguna. */
  public RuleConfigResponse get(CurrentUser user, String language, RuleKind kind) {
    return repository
        .findByUserIdAndLanguageAndKind(user.id(), language, kind)
        .map(rc -> new RuleConfigResponse(language, kind, false, rc.getConfig()))
        .orElseGet(
            () -> new RuleConfigResponse(language, kind, true, defaultConfig(language, kind)));
  }

  /** Upsert de la config del usuario. Devuelve lo guardado con {@code isDefault = false}. */
  public RuleConfigResponse save(
      CurrentUser user, String language, RuleKind kind, JsonNode config) {
    // Valida que el lenguaje exista (tiene defaults) antes de persistir.
    requireSupported(language, kind);
    RuleConfig saved =
        repository
            .findByUserIdAndLanguageAndKind(user.id(), language, kind)
            .map(rc -> {
              rc.setConfig(config);
              return rc;
            })
            .orElseGet(() -> new RuleConfig(user.id(), language, kind, config));
    repository.save(saved);
    return new RuleConfigResponse(language, kind, false, config);
  }

  private void requireSupported(String language, RuleKind kind) {
    if (!defaultResource(language, kind).exists()) {
      throw new UnsupportedLanguageException("Language " + language + " is not supported");
    }
  }

  private JsonNode defaultConfig(String language, RuleKind kind) {
    ClassPathResource resource = defaultResource(language, kind);
    if (!resource.exists()) {
      throw new UnsupportedLanguageException("Language " + language + " is not supported");
    }
    try (var in = resource.getInputStream()) {
      return objectMapper.readTree(in);
    } catch (IOException ex) {
      throw new UncheckedIOException(
          "Could not read default rules for " + language + "/" + kind, ex);
    }
  }

  private ClassPathResource defaultResource(String language, RuleKind kind) {
    String name = "rules/" + language + "-" + kind.name().toLowerCase(Locale.ROOT) + ".json";
    return new ClassPathResource(name);
  }
}
