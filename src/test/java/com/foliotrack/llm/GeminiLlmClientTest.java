package com.foliotrack.llm;

import com.foliotrack.exception.LlmUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// MockRestServiceServer intercepts the RestClient's HTTP calls, so we check the exact request we send
// to Gemini and how we read its replies without a network connection or a real API key.
class GeminiLlmClientTest {

    private static final String BASE_URL = "https://gemini.test/v1beta";
    private static final String ENDPOINT = BASE_URL + "/models/test-model:generateContent";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private GeminiLlmClient client(String apiKey) {
        return new GeminiLlmClient(builder, BASE_URL, apiKey, "test-model");
    }

    @Test
    void sendsPromptsWithKeyInHeaderAndReturnsJoinedText() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("system prompt"))
                .andExpect(jsonPath("$.systemInstruction.role").doesNotExist())
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("question"))
                .andRespond(withSuccess("""
                        {"candidates": [{"content": {"role": "model",
                          "parts": [{"text": "SELECT * "}, {"text": "FROM v_my_holdings\\n"}]},
                          "finishReason": "STOP"}],
                         "usageMetadata": {"totalTokenCount": 42}}
                        """, MediaType.APPLICATION_JSON));

        String reply = client("secret-key").complete("system prompt", "question");

        assertThat(reply).isEqualTo("SELECT * FROM v_my_holdings");
        server.verify();
    }

    @Test
    void missingApiKeyFailsWithoutCallingGemini() {
        assertThatThrownBy(() -> client("").complete("system", "question"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("not configured");
        server.verify();
    }

    @Test
    void rateLimitBecomesLlmUnavailable() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\"}}"));

        assertThatThrownBy(() -> client("secret-key").complete("system", "question"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("try again later");
    }

    @Test
    void serverErrorBecomesLlmUnavailable() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client("secret-key").complete("system", "question"))
                .isInstanceOf(LlmUnavailableException.class);
    }

    // e.g. a response blocked by safety filters comes back with no candidate text
    @Test
    void emptyCandidatesBecomeLlmUnavailable() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"candidates\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("secret-key").complete("system", "question"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("no answer");
    }
}
