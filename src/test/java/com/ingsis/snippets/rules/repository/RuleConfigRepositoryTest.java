package com.ingsis.snippets.rules.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.rules.model.RuleConfig;
import com.ingsis.snippets.rules.model.RuleKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class RuleConfigRepositoryTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private RuleConfigRepository repository;

  @Test
  void savesAndReadsBackOpaqueJsonConfig() {
    var config = mapper.readTree("{\"identifierFormat\":\"snake case\",\"max\":3}");
    repository.save(new RuleConfig("user-ana", "printscript", RuleKind.LINT, config));

    var found =
        repository
            .findByUserIdAndLanguageAndKind("user-ana", "printscript", RuleKind.LINT)
            .orElseThrow();
    assertThat(found.getConfig().get("identifierFormat").asString()).isEqualTo("snake case");
    assertThat(found.getConfig().get("max").asInt()).isEqualTo(3);
    assertThat(found.getUpdatedAt()).isNotNull();
  }

  @Test
  void compositeKeySeparatesKinds() {
    repository.save(
        new RuleConfig("user-ana", "printscript", RuleKind.LINT, mapper.readTree("{\"a\":1}")));
    repository.save(
        new RuleConfig("user-ana", "printscript", RuleKind.FORMAT, mapper.readTree("{\"b\":2}")));

    assertThat(repository.findByUserIdAndLanguageAndKind("user-ana", "printscript", RuleKind.LINT))
        .isPresent();
    assertThat(
            repository.findByUserIdAndLanguageAndKind("user-ana", "printscript", RuleKind.FORMAT))
        .isPresent();
    assertThat(repository.count()).isEqualTo(2);
  }

  @Test
  void missingConfigIsEmpty() {
    assertThat(repository.findByUserIdAndLanguageAndKind("nobody", "printscript", RuleKind.LINT))
        .isEmpty();
  }
}
