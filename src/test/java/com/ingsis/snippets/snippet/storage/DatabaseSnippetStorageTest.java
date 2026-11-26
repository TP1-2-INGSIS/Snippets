package com.ingsis.snippets.snippet.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class DatabaseSnippetStorageTest {

  @Autowired private SnippetRepository snippets;

  private SnippetStorage storage;

  @BeforeEach
  void setUp() {
    snippets.deleteAll();
    storage = new DatabaseSnippetStorage(snippets);
  }

  private UUID persist(@org.jspecify.annotations.Nullable String content) {
    Snippet snippet =
        new Snippet(UUID.randomUUID(), "n", null, "printscript", "1.1", content, "user-ana");
    return snippets.save(snippet).getId();
  }

  @Test
  void getReturnsTheStoredContent() {
    UUID id = persist("println(1);");
    assertThat(storage.get(id)).isEqualTo("println(1);");
  }

  @Test
  void saveOverwritesTheContent() {
    UUID id = persist("old");
    storage.save(id, "new content");
    assertThat(snippets.findById(id).orElseThrow().getContent()).isEqualTo("new content");
  }

  @Test
  void removeLeavesTheRowToTheCaller() {
    UUID id = persist("kept");
    storage.remove(id);
    assertThat(snippets.findById(id)).isPresent();
  }

  @Test
  void getOnARowWithoutContentFails() {
    UUID id = persist(null);
    assertThatThrownBy(() -> storage.get(id))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no content");
  }

  @Test
  void getOnMissingSnippetFails() {
    UUID missing = UUID.randomUUID();
    assertThatThrownBy(() -> storage.get(missing)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void saveOnMissingSnippetFails() {
    UUID missing = UUID.randomUUID();
    assertThatThrownBy(() -> storage.save(missing, "x")).isInstanceOf(IllegalStateException.class);
  }
}
