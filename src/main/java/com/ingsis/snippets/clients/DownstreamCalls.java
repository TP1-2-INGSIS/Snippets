package com.ingsis.snippets.clients;

import java.util.function.Function;
import java.util.function.Supplier;

import com.ingsis.snippets.common.error.ApiException;
import com.ingsis.snippets.common.error.DownstreamException;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Runs a request against a downstream service and translates its failures (D11).
 * <ul>
 * <li>A 4xx whose code the translator recognizes becomes that domain exception.</li>
 * <li>Anything else (5xx, unknown 4xx, timeout, connection or body error, empty body)
 * becomes {@link DownstreamException}: for the UI it is the dependency that failed.</li>
 * </ul>
 */
public final class DownstreamCalls {

	private DownstreamCalls() {
	}

	/** @param translator returns the domain exception for a known error, or {@code null} */
	public static <T> T call(String service, Supplier<T> request, Function<DownstreamError, ApiException> translator) {
		T response;
		try {
			response = request.get();
		}
		catch (RestClientResponseException ex) {
			throw translate(service, ex, translator);
		}
		catch (RestClientException ex) {
			throw new DownstreamException(service + " is unavailable", ex);
		}
		if (response == null) {
			throw new DownstreamException(service + " returned an empty response", null);
		}
		return response;
	}

	private static ApiException translate(String service, RestClientResponseException ex,
			Function<DownstreamError, ApiException> translator) {
		if (ex.getStatusCode().is4xxClientError()) {
			DownstreamError error = parse(ex);
			ApiException translated = error == null || error.error() == null ? null : translator.apply(error);
			if (translated != null) {
				return translated;
			}
		}
		return new DownstreamException(service + " responded " + ex.getStatusCode().value(), ex);
	}

	private static DownstreamError parse(RestClientResponseException ex) {
		try {
			return ex.getResponseBodyAs(DownstreamError.class);
		}
		catch (RuntimeException unreadable) {
			return null;
		}
	}

}
