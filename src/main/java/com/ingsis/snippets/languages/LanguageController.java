package com.ingsis.snippets.languages;

import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.LanguageInfo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxies the available languages so the UI can build its language/version selector (D01). If
 * Language Service fails, the client already turns it into a 502 (D11).
 */
@RestController
class LanguageController {

  private final LanguageClient languages;

  LanguageController(LanguageClient languages) {
    this.languages = languages;
  }

  @GetMapping("/languages")
  List<LanguageInfo> languages() {
    return languages.languages();
  }
}
