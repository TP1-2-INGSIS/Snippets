package com.ingsis.snippets.snippet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.model.SourceViolation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SnippetRepositoryTest {

  @Autowired private SnippetRepository snippets;

  private UUID hola;
  private UUID loop;
  private UUID other;

  @BeforeEach
  void seed() {
    snippets.deleteAll();
    hola = save("Hola mundo", "printscript", "1.1", "user-ana", LintStatus.COMPLIANT);
    loop = save("Loop demo", "printscript", "1.0", "user-ana", LintStatus.NON_COMPLIANT);
    other = save("Python thing", "python", "3.12", "user-ana", LintStatus.PENDING);
  }

  private UUID save(String name, String language, String version, String owner, LintStatus status) {
    Snippet snippet =
        new Snippet(UUID.randomUUID(), name, null, language, version, "content of " + name, owner);
    snippet.setLintStatus(status);
    return snippets.save(snippet).getId();
  }

  private PageRequest firstPage() {
    return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt"));
  }

  @Test
  void savesAndReadsBackAllFields() {
    Snippet snippet =
        new Snippet(
            UUID.randomUUID(),
            "With violations",
            "a desc",
            "printscript",
            "1.1",
            "println(x);",
            "user-ana");
    snippet.setLintStatus(LintStatus.NON_COMPLIANT);
    snippet.setLintViolations(List.of(new SourceViolation("identifierFormat", "bad name", 1, 5)));

    UUID id = snippets.save(snippet).getId();
    snippets.flush();

    Snippet found = snippets.findById(id).orElseThrow();
    assertThat(found.getName()).isEqualTo("With violations");
    assertThat(found.getDescription()).isEqualTo("a desc");
    assertThat(found.getContent()).isEqualTo("println(x);");
    assertThat(found.getOwnerId()).isEqualTo("user-ana");
    assertThat(found.getLintStatus()).isEqualTo(LintStatus.NON_COMPLIANT);
    assertThat(found.getLintViolations())
        .singleElement()
        .satisfies(v -> {
          assertThat(v.rule()).isEqualTo("identifierFormat");
          assertThat(v.line()).isEqualTo(1);
          assertThat(v.column()).isEqualTo(5);
        });
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
  }

  @Test
  void onlyReturnsSnippetsWhoseIdIsInTheList() {
    var page = snippets.searchByName(List.of(hola, loop), null, null, null, firstPage());
    assertThat(page.getContent()).extracting(Snippet::getId).containsExactlyInAnyOrder(hola, loop);
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void filtersByNameCaseInsensitiveContains() {
    var page = snippets.searchByName(List.of(hola, loop, other), "hola", null, null, firstPage());
    assertThat(page.getContent()).extracting(Snippet::getId).containsExactly(hola);
  }

  @Test
  void filtersByLanguage() {
    var page = snippets.searchByName(List.of(hola, loop, other), null, "python", null, firstPage());
    assertThat(page.getContent()).extracting(Snippet::getId).containsExactly(other);
  }

  @Test
  void filtersByLintStatus() {
    var page =
        snippets.searchByName(
            List.of(hola, loop, other), null, null, LintStatus.NON_COMPLIANT, firstPage());
    assertThat(page.getContent()).extracting(Snippet::getId).containsExactly(loop);
  }

  @Test
  void ordersByNameAscending() {
    var page =
        snippets.searchByName(
            List.of(hola, loop, other),
            null,
            null,
            null,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name")));
    assertThat(page.getContent())
        .extracting(Snippet::getName)
        .containsExactly("Hola mundo", "Loop demo", "Python thing");
  }

  @Test
  void paginates() {
    var firstOfOne =
        snippets.searchByName(
            List.of(hola, loop, other),
            null,
            null,
            null,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "name")));
    assertThat(firstOfOne.getContent()).extracting(Snippet::getName).containsExactly("Hola mundo");
    assertThat(firstOfOne.getTotalElements()).isEqualTo(3);
    assertThat(firstOfOne.getTotalPages()).isEqualTo(3);
  }
}
