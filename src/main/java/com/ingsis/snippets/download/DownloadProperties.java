package com.ingsis.snippets.download;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Extensión de archivo por lenguaje para la descarga ({@code download.extensions}).
 * Un lenguaje sin entrada usa {@link #fallback}.
 */
@ConfigurationProperties("download")
public record DownloadProperties(Map<String, String> extensions, String fallback) {

	public DownloadProperties {
		if (extensions == null) {
			extensions = Map.of();
		}
		if (fallback == null || fallback.isBlank()) {
			fallback = "txt";
		}
	}

	public String extensionFor(String language) {
		return extensions.getOrDefault(language, fallback);
	}

}
