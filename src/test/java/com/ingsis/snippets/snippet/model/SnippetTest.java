package com.ingsis.snippets.snippet.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SnippetTest {

  @Autowired private SnippetRepository snippets;

  @Test
  void prePersistSetsBothTimestampsAndPreUpdateMovesUpdatedAt() throws InterruptedException {
    Snippet snippet =
        new Snippet(UUID.randomUUID(), "n", "d", "printscript", "1.1", "c", "user-ana");
    Snippet saved = snippets.saveAndFlush(snippet);

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());

    Thread.sleep(5);
    saved.setName("renamed");
    Snippet updated = snippets.saveAndFlush(saved);

    assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
  }

  @Test
  void mutatorsUpdateEveryField() {
    Snippet snippet =
        new Snippet(UUID.randomUUID(), "n", "d", "printscript", "1.1", "c", "user-ana");

    snippet.setName("new name");
    snippet.setDescription("new desc");
    snippet.setLanguage("python");
    snippet.setVersion("3.12");
    snippet.setContent("new content");
    snippet.setLintStatus(LintStatus.COMPLIANT);
    snippet.setLintViolations(List.of(new SourceViolation("r", "m", null, null)));

    assertThat(snippet.getName()).isEqualTo("new name");
    assertThat(snippet.getDescription()).isEqualTo("new desc");
    assertThat(snippet.getLanguage()).isEqualTo("python");
    assertThat(snippet.getVersion()).isEqualTo("3.12");
    assertThat(snippet.getContent()).isEqualTo("new content");
    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.COMPLIANT);
    assertThat(snippet.getLintViolations())
        .singleElement()
        .satisfies(v -> {
          assertThat(v.rule()).isEqualTo("r");
          assertThat(v.message()).isEqualTo("m");
          assertThat(v.line()).isNull();
          assertThat(v.column()).isNull();
        });
  }
}
