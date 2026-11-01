package com.ingsis.snippets.common.error;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that throw each kind of error. */
@RestController
@RequestMapping("/probe")
class ErrorProbeController {

	record Body(@NotBlank String name) {
	}

	@GetMapping("/not-found")
	void notFound() {
		throw new NotFoundException("Snippet not found");
	}

	@GetMapping("/forbidden")
	void forbidden() {
		throw new ForbiddenException("Not allowed");
	}

	@GetMapping("/unsupported-language")
	void unsupportedLanguage() {
		throw new UnsupportedLanguageException("Language cobol is not supported");
	}

	@GetMapping("/bad-request")
	void badRequest() {
		throw new BadRequestException("Invalid rules");
	}

	@GetMapping("/invalid-snippet")
	void invalidSnippet() {
		throw new InvalidSnippetException(List.of(Map.of("rule", "UNEXPECTED_TOKEN", "line", 1, "column", 16)));
	}

	@GetMapping("/conflict")
	void conflict() {
		throw new ConflictException(ErrorCode.ALREADY_SHARED, "Already shared");
	}

	@GetMapping("/downstream")
	void downstream() {
		throw new DownstreamException("Language Service unavailable", new RuntimeException("connection refused"));
	}

	@GetMapping("/unexpected")
	void unexpected() {
		throw new IllegalStateException("secret internal detail");
	}

	@GetMapping("/param")
	void param(@RequestParam int page) {
	}

	@PostMapping("/body")
	void body(@Valid @RequestBody Body body) {
	}

}
