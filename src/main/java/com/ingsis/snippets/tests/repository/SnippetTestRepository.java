package com.ingsis.snippets.tests.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ingsis.snippets.tests.model.SnippetTest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SnippetTestRepository extends JpaRepository<SnippetTest, UUID> {

	List<SnippetTest> findBySnippetId(UUID snippetId);

	/** Un test solo es accesible dentro de su snippet: si no coincide, no existe (404). */
	Optional<SnippetTest> findByIdAndSnippetId(UUID id, UUID snippetId);

}
