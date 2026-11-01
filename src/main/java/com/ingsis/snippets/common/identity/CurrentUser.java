package com.ingsis.snippets.common.identity;

/**
 * The user making the request. Declare it as a controller method parameter to receive it.
 * <p>
 * Today it comes from the {@code X-User-Id} header; with Auth0 it will come from the JWT.
 * Only {@link CurrentUserInterceptor} knows where it comes from (D02).
 */
public record CurrentUser(String id) {
}
