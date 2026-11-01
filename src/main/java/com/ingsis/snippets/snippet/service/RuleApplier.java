package com.ingsis.snippets.snippet.service;

import java.util.List;
import java.util.UUID;

import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.SourceError;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.SnippetPermission;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Aplica reglas a todos los snippets OWNER del usuario en un lenguaje, sincrónicamente
 * dentro del request (D14). Si un snippet falla, se registra y se sigue con el siguiente.
 * Aislado acá para poder moverlo a un consumer (Redis Streams) sin tocar controllers.
 */
@Service
public class RuleApplier {

	private static final Logger log = LoggerFactory.getLogger(RuleApplier.class);

	private final SnippetRepository snippets;

	private final PermissionClient permissions;

	private final LanguageClient language;

	public RuleApplier(SnippetRepository snippets, PermissionClient permissions, LanguageClient language) {
		this.snippets = snippets;
		this.permissions = permissions;
		this.language = language;
	}

	/** Re-lintea: cada snippet que falla queda {@code FAILED}. */
	@Transactional
	public RuleApplyResult relint(CurrentUser user, String languageId, JsonNode rules) {
		return apply(user, languageId, snippet -> {
			List<SourceError> violations = language.lint(languageId, snippet.getContent(), snippet.getVersion(),
				rules);
			LintResults.apply(snippet, violations);
		});
	}

	/** Re-formatea: reescribe el contenido; un snippet que falla queda sin cambios. */
	@Transactional
	public RuleApplyResult reformat(CurrentUser user, String languageId, JsonNode rules) {
		return apply(user, languageId, snippet -> {
			String formatted = language.format(languageId, snippet.getContent(), snippet.getVersion(), rules);
			snippet.setContent(formatted);
		});
	}

	private RuleApplyResult apply(CurrentUser user, String languageId, Operation operation) {
		List<UUID> ownedIds = permissions.idsFor(user.id(), PermissionRole.OWNER)
			.stream()
			.map(SnippetPermission::snippetId)
			.toList();
		int processed = 0;
		int failed = 0;
		for (Snippet snippet : snippets.findAllById(ownedIds)) {
			if (!snippet.getLanguage().equals(languageId)) {
				continue;
			}
			processed++;
			try {
				operation.run(snippet);
				snippets.save(snippet);
			}
			catch (RuntimeException ex) {
				failed++;
				snippet.setLintStatus(LintStatus.FAILED);
				snippets.save(snippet);
				log.warn("Rule application failed for snippet {}", snippet.getId(), ex);
			}
		}
		return new RuleApplyResult(processed, failed);
	}

	@FunctionalInterface
	private interface Operation {

		void run(Snippet snippet);

	}

	/** Cuántos snippets se procesaron y cuántos fallaron. */
	public record RuleApplyResult(int processed, int failed) {
	}

}
