package com.ingsis.snippets.snippet.model;

/** "Validez" de un snippet (US 5, §4). Se persiste, no se calcula al vuelo (D08). */
public enum LintStatus {

	COMPLIANT,
	NON_COMPLIANT,
	PENDING,
	FAILED

}
