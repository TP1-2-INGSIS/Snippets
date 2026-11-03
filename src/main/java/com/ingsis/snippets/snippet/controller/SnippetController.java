package com.ingsis.snippets.snippet.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.validation.Valid;

import com.ingsis.snippets.common.error.BadRequestException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.snippet.dto.CreateSnippetRequest;
import com.ingsis.snippets.snippet.dto.SnippetDetail;
import com.ingsis.snippets.snippet.dto.SnippetPage;
import com.ingsis.snippets.snippet.dto.UpdateSnippetRequest;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Relation;
import com.ingsis.snippets.snippet.service.SnippetService;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints de snippets (US 1–6). La autorización la resuelve {@link SnippetService} vía Permission. */
@RestController
class SnippetController {

	private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of("name", "language", "lintStatus",
			"updatedAt");

	private final SnippetService service;

	SnippetController(SnippetService service) {
		this.service = service;
	}

	@PostMapping("/snippets")
	@ResponseStatus(HttpStatus.CREATED)
	SnippetDetail create(CurrentUser user, @Valid @RequestBody CreateSnippetRequest request) {
		return service.create(user, request.name(), request.description(), request.language(), request.version(),
			request.content());
	}

	@PostMapping("/snippets/upload")
	@ResponseStatus(HttpStatus.CREATED)
	SnippetDetail upload(CurrentUser user, @RequestParam MultipartFile file, @RequestParam String name,
			@RequestParam(required = false) @Nullable String description, @RequestParam String language,
			@RequestParam String version) {
		return service.create(user, name, description, language, version, read(file));
	}

	@GetMapping("/snippets/{id}")
	SnippetDetail get(CurrentUser user, @PathVariable UUID id) {
		return service.get(user, id);
	}

	@GetMapping("/snippets")
	SnippetPage list(CurrentUser user, @RequestParam(defaultValue = "ALL") Relation relation,
			@RequestParam(required = false) @Nullable String name,
			@RequestParam(required = false) @Nullable String language,
			@RequestParam(required = false) @Nullable LintStatus lintStatus,
			@RequestParam(defaultValue = "updatedAt") String sort, @RequestParam(defaultValue = "desc") String order,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		if (!SORT_FIELDS.contains(sort)) {
			throw new BadRequestException("Invalid sort field: " + sort);
		}
		Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
		PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sort));
		return service.list(user, relation, name, language, lintStatus, pageable);
	}

	@PutMapping("/snippets/{id}")
	SnippetDetail update(CurrentUser user, @PathVariable UUID id, @RequestBody UpdateSnippetRequest request) {
		return service.update(user, id, request);
	}

	@PutMapping("/snippets/{id}/upload")
	SnippetDetail updateUpload(CurrentUser user, @PathVariable UUID id, @RequestParam MultipartFile file,
			@RequestParam(required = false) @Nullable String name,
			@RequestParam(required = false) @Nullable String description,
			@RequestParam(required = false) @Nullable String language,
			@RequestParam(required = false) @Nullable String version) {
		return service.update(user, id, new UpdateSnippetRequest(name, description, language, version, read(file)));
	}

	@DeleteMapping("/snippets/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(CurrentUser user, @PathVariable UUID id) {
		service.delete(user, id);
	}

	private static String read(MultipartFile file) {
		try {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read uploaded file", ex);
		}
	}

}
