package com.ingsis.snippets.rules.repository;

import com.ingsis.snippets.rules.model.RuleConfig;
import com.ingsis.snippets.rules.model.RuleConfigId;
import com.ingsis.snippets.rules.model.RuleKind;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleConfigRepository extends JpaRepository<RuleConfig, RuleConfigId> {

  Optional<RuleConfig> findByUserIdAndLanguageAndKind(
      String userId, String language, RuleKind kind);
}
