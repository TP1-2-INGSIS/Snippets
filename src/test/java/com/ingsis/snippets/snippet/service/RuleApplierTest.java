package com.ingsis.snippets.snippet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.SnippetPermission;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RuleApplierTest {

  private final CurrentUser ana = new CurrentUser("user-ana");

  private final JsonNode rules = new ObjectMapper().readTree("{\"a\":1}");

  @Mock private SnippetRepository snippets;

  @Mock private PermissionClient permissions;

  @Mock private LanguageClient language;

  private RuleApplier applier;

  @BeforeEach
  void setUp() {
    applier = new RuleApplier(snippets, permissions, language);
  }

  private Snippet snippet(String lang) {
    return new Snippet(UUID.randomUUID(), "n", null, lang, "1.1", "code", "user-ana");
  }

  @Test
  void relintProcessesOnlyOwnedSnippetsOfTheLanguageAndCountsFailures() {
    Snippet boomA = snippet("printscript");
    Snippet boomB = snippet("printscript");
    Snippet otherLang = snippet("python");
    given(permissions.idsFor("user-ana", PermissionRole.OWNER))
        .willReturn(
            List.of(
                new SnippetPermission(boomA.getId(), PermissionRole.OWNER),
                new SnippetPermission(boomB.getId(), PermissionRole.OWNER),
                new SnippetPermission(otherLang.getId(), PermissionRole.OWNER)));
    given(snippets.findAllById(any())).willReturn(List.of(boomA, boomB, otherLang));
    org.mockito.BDDMockito.willThrow(new DownstreamException("lang down", null))
        .given(language)
        .lint(eq("printscript"), eq("code"), any(), any());

    var result = applier.relint(ana, "printscript", rules);

    assertThat(result.processed()).isEqualTo(2); // only the two printscript snippets
    assertThat(result.failed()).isEqualTo(2);
    assertThat(boomA.getLintStatus()).isEqualTo(LintStatus.FAILED);
    assertThat(otherLang.getLintStatus()).isNotEqualTo(LintStatus.FAILED);
  }

  @Test
  void relintHappyPathMarksCompliant() {
    Snippet ok = snippet("printscript");
    given(permissions.idsFor("user-ana", PermissionRole.OWNER))
        .willReturn(List.of(new SnippetPermission(ok.getId(), PermissionRole.OWNER)));
    given(snippets.findAllById(any())).willReturn(List.of(ok));
    given(language.lint(eq("printscript"), any(), any(), any())).willReturn(List.of());

    var result = applier.relint(ana, "printscript", rules);

    assertThat(result.processed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(ok.getLintStatus()).isEqualTo(LintStatus.COMPLIANT);
  }

  @Test
  void reformatRewritesContentAndSkipsOtherLanguages() {
    Snippet ps = snippet("printscript");
    Snippet py = snippet("python");
    given(permissions.idsFor("user-ana", PermissionRole.OWNER))
        .willReturn(
            List.of(
                new SnippetPermission(ps.getId(), PermissionRole.OWNER),
                new SnippetPermission(py.getId(), PermissionRole.OWNER)));
    given(snippets.findAllById(any())).willReturn(List.of(ps, py));
    given(language.format(eq("printscript"), eq("code"), any(), any()))
        .willReturn("formatted code");

    var result = applier.reformat(ana, "printscript", rules);

    assertThat(result.processed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(ps.getContent()).isEqualTo("formatted code");
    assertThat(py.getContent()).isEqualTo("code");
    verify(language).format(eq("printscript"), any(), any(), any());
  }
}
