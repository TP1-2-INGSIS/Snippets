package com.ingsis.snippets.common.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint that echoes the resolved user. */
@RestController
class IdentityProbeController {

	@GetMapping("/probe/me")
	String me(CurrentUser user) {
		return user.id();
	}

}
