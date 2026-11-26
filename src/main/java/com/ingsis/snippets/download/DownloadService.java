package com.ingsis.snippets.download;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.snippet.storage.SnippetStorage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Descarga de un snippet, original o formateado (US 13). Si hay {@code formattedContent} guardado
 * se usa; si no, se formatea al vuelo con las reglas FORMAT del **owner** del snippet, no las de
 * quien descarga (D15).
 */
@Service
public class DownloadService {

  private final AuthorizationService authorization;

  private final SnippetRepository snippets;

  private final LanguageClient language;

  private final RulesService rules;

  private final DownloadProperties properties;

  private final SnippetStorage storage;

  public DownloadService(
      AuthorizationService authorization,
      SnippetRepository snippets,
      LanguageClient language,
      RulesService rules,
      DownloadProperties properties,
      SnippetStorage storage) {
    this.authorization = authorization;
    this.snippets = snippets;
    this.language = language;
    this.rules = rules;
    this.properties = properties;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public DownloadContent download(CurrentUser user, UUID id, boolean formatted) {
    authorization.require(user, id, PermissionAction.READ);
    Snippet snippet =
        snippets
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Snippet " + id + " not found"));

    String content;
    if (formatted && snippet.getFormattedContent() != null) {
      content = snippet.getFormattedContent();
    } else if (formatted) {
      // Reglas del owner (D15): se leen con su id, no con el del que descarga.
      CurrentUser owner = new CurrentUser(snippet.getOwnerId());
      var ownerRules = rules.get(owner, snippet.getLanguage(), RuleKind.FORMAT).config();
      content =
          language.format(snippet.getLanguage(), storage.get(id), snippet.getVersion(), ownerRules);
    } else {
      content = storage.get(id);
    }
    String filename = snippet.getName() + "." + properties.extensionFor(snippet.getLanguage());
    return new DownloadContent(filename, content);
  }
}
