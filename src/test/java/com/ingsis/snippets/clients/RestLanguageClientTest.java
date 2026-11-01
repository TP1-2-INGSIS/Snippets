package com.ingsis.snippets.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.LanguageInfo;
import com.ingsis.snippets.clients.language.SourceError;
import com.ingsis.snippets.clients.language.TestResult;
import com.ingsis.snippets.clients.language.ValidationResult;
import com.ingsis.snippets.common.error.ApiException;
import com.ingsis.snippets.common.error.BadRequestException;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.common.error.ErrorCode;
import com.ingsis.snippets.common.error.InvalidSnippetException;
import com.ingsis.snippets.common.error.UnsupportedLanguageException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

class RestLanguageClientTest {

	private static final String CONTENT = "println(1);";

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	private LanguageClient client;

	@BeforeEach
	void setUp() {
		client = WireMockClients.language(server.baseUrl(), Duration.ofSeconds(10));
	}

	private static void stubPostError(String operation, int status, String code) {
		server.stubFor(post("/languages/printscript/" + operation).willReturn(aResponse().withStatus(status)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":\"" + code + "\",\"message\":\"nope\",\"details\":[]}")));
	}

	@Test
	void languages() {
		server.stubFor(get("/languages").willReturn(okJson("[{\"id\":\"printscript\",\"versions\":[\"1.0\",\"1.1\"]}]")));

		assertThat(client.languages()).containsExactly(new LanguageInfo("printscript", List.of("1.0", "1.1")));
	}

	@Test
	void validateSendsContentAndVersionAndReturnsErrors() {
		server.stubFor(post("/languages/printscript/validate")
			.withRequestBody(equalToJson("{\"content\":\"println(1);\",\"version\":\"1.1\"}"))
			.willReturn(okJson("{\"valid\":false,\"errors\":[{\"rule\":\"UNEXPECTED_TOKEN\",\"message\":\"Expected ;\","
					+ "\"line\":1,\"column\":16}]}")));

		ValidationResult result = client.validate("printscript", CONTENT, "1.1");

		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).containsExactly(new SourceError("UNEXPECTED_TOKEN", "Expected ;", 1, 16));
	}

	@Test
	void formatSendsRulesAndReturnsFormattedSource() {
		JsonNode rules = JsonNodeFactory.instance.objectNode().put("spaceBeforeColon", true);
		server.stubFor(post("/languages/printscript/format")
			.withRequestBody(equalToJson(
					"{\"content\":\"println(1);\",\"version\":\"1.1\",\"rules\":{\"spaceBeforeColon\":true}}"))
			.willReturn(okJson("{\"formatted\":\"println(1);\\n\"}")));

		assertThat(client.format("printscript", CONTENT, "1.1", rules)).isEqualTo("println(1);\n");
	}

	@Test
	void formatOmitsRulesWhenNull() {
		server.stubFor(post("/languages/printscript/format").willReturn(okJson("{\"formatted\":\"x\"}")));

		client.format("printscript", CONTENT, "1.1", null);

		server.verify(postRequestedFor(urlEqualTo("/languages/printscript/format"))
			.withRequestBody(equalToJson("{\"content\":\"println(1);\",\"version\":\"1.1\"}")));
	}

	@Test
	void lintReturnsViolations() {
		server.stubFor(post("/languages/printscript/lint").willReturn(okJson(
				"{\"violations\":[{\"rule\":\"identifierFormat\",\"message\":\"m\",\"line\":1,\"column\":5}]}")));

		assertThat(client.lint("printscript", CONTENT, "1.1", null))
			.containsExactly(new SourceError("identifierFormat", "m", 1, 5));
	}

	@Test
	void testSendsTheAssertAndReturnsItsResult() {
		server.stubFor(post("/languages/printscript/test")
			.withRequestBody(equalToJson("{\"content\":\"println(1);\",\"version\":\"1.1\",\"inputs\":[\"a\"],"
					+ "\"expectedOutputs\":[\"1\"]}"))
			.willReturn(okJson("{\"passed\":false,\"expectedOutputs\":[\"1\"],\"actualOutputs\":[],"
					+ "\"error\":{\"rule\":\"TIMEOUT\",\"message\":\"m\",\"line\":1,\"column\":1}}")));

		TestResult result = client.test("printscript", CONTENT, "1.1", List.of("a"), List.of("1"));

		assertThat(result.passed()).isFalse();
		assertThat(result.actualOutputs()).isEmpty();
		assertThat(result.error().rule()).isEqualTo("TIMEOUT");
	}

	@Test
	void languageIdIsEncodedInThePath() {
		server.stubFor(post(urlEqualTo("/languages/a%2Fb/lint")).willReturn(okJson("{\"violations\":[]}")));

		assertThat(client.lint("a/b", CONTENT, "1.1", null)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "LANGUAGE_NOT_FOUND", "UNSUPPORTED_VERSION" })
	void unknownLanguageOrVersionIsUnsupportedLanguage(String code) {
		stubPostError("validate", 400, code);

		assertThatThrownBy(() -> client.validate("printscript", CONTENT, "9.9"))
			.isInstanceOf(UnsupportedLanguageException.class)
			.hasMessage("nope");
	}

	@Test
	void invalidRulesIsBadRequest() {
		stubPostError("lint", 400, "INVALID_RULES");

		assertThatThrownBy(() -> client.lint("printscript", CONTENT, "1.1", null))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void invalidSourceKeepsTheErrorsAsDetails() {
		server.stubFor(post("/languages/printscript/format").willReturn(aResponse().withStatus(422)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":\"INVALID_SOURCE\",\"message\":\"m\",\"details\":[{\"rule\":\"R\",\"line\":2}]}")));

		assertThatThrownBy(() -> client.format("printscript", CONTENT, "1.1", null))
			.isInstanceOfSatisfying(InvalidSnippetException.class,
					ex -> assertThat(ex.details()).hasSize(1).first().hasToString("{rule=R, line=2}"));
	}

	@Test
	void unknownClientErrorIsDownstream() {
		stubPostError("lint", 400, "BAD_REQUEST");

		assertDownstream(() -> client.lint("printscript", CONTENT, "1.1", null));
	}

	@Test
	void clientErrorWithUnreadableBodyIsDownstream() {
		server.stubFor(post("/languages/printscript/lint")
			.willReturn(aResponse().withStatus(400).withHeader("Content-Type", "text/plain").withBody("oops")));

		assertDownstream(() -> client.lint("printscript", CONTENT, "1.1", null));
	}

	@Test
	void serverErrorIsDownstream() {
		server.stubFor(get("/languages").willReturn(serverError()));

		assertDownstream(client::languages);
	}

	@Test
	void malformedSuccessBodyIsDownstream() {
		server.stubFor(get("/languages").willReturn(okJson("{not json")));

		assertDownstream(client::languages);
	}

	@Test
	void emptySuccessBodyIsDownstream() {
		server.stubFor(get("/languages").willReturn(aResponse().withStatus(200)));

		assertDownstream(client::languages);
	}

	@Test
	void slowResponseTimesOut() {
		// The short timeout only applies here: a cold first request can take over a second.
		LanguageClient impatient = WireMockClients.language(server.baseUrl(), Duration.ofMillis(200));
		server.stubFor(get("/languages").willReturn(okJson("[]").withFixedDelay(1_000)));

		assertDownstream(impatient::languages);
	}

	@Test
	void unreachableServiceIsDownstream() {
		LanguageClient unreachable = WireMockClients.language("http://localhost:1", Duration.ofSeconds(10));

		assertDownstream(unreachable::languages);
	}

	private static void assertDownstream(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(DownstreamException.class)
			.extracting(ex -> ((ApiException) ex).code())
			.isEqualTo(ErrorCode.DOWNSTREAM_ERROR);
	}

}
