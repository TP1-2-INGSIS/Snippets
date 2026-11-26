package com.ingsis.snippets.common.identity;

/**
 * The user making the request. Declare it as a controller method parameter to receive it.
 *
 * <p>It comes from the {@code sub} claim of the validated Auth0 access token (or, only when {@code
 * AUTH_HEADER_IDENTITY_ENABLED=true}, from the {@code X-User-Id} header). See {@link
 * SecurityConfig} (D02).
 */
public record CurrentUser(String id) {}
