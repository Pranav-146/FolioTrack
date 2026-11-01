package com.foliotrack.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.foliotrack.exception.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// Google Gemini via its REST generateContent endpoint: plain HTTP + JSON, so no vendor SDK is needed.
// Active when foliotrack.llm.provider=gemini (the default); another provider would be another LlmClient bean.
@Component
@ConditionalOnProperty(name = "foliotrack.llm.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    // RestClient.Builder is auto-configured by Spring Boot (JSON converters, spring.http.client timeouts)
    public GeminiLlmClient(RestClient.Builder restClientBuilder,
                           @Value("${foliotrack.llm.base-url}") String baseUrl,
                           @Value("${foliotrack.llm.api-key}") String apiKey,
                           @Value("${foliotrack.llm.model}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmUnavailableException("The AI assistant is not configured (LLM_API_KEY is not set)");
        }
        GenerateContentRequest request = new GenerateContentRequest(
                new Content(null, List.of(new Part(systemPrompt))),
                List.of(new Content("user", List.of(new Part(userMessage)))));

        GenerateContentResponse response;
        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    // Key in a header, not the ?key= query param, so it can't end up in URL/access logs
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateContentResponse.class);
        } catch (HttpStatusCodeException e) {
            // 429 = free-tier rate limit. The response body is Google's error JSON, which never contains our key.
            log.warn("Gemini returned HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new LlmUnavailableException("The AI assistant is unavailable right now; please try again later");
        } catch (RestClientException e) {
            log.warn("Gemini request failed: {}", e.getMessage());
            throw new LlmUnavailableException("The AI assistant is unavailable right now; please try again later");
        }

        String text = extractText(response);
        if (text.isBlank()) {
            throw new LlmUnavailableException("The AI assistant returned no answer; please try again");
        }
        return text;
    }

    // A reply can be split across several parts, and a candidate can be empty (e.g. blocked by safety
    // filters), so join every text part of the first candidate instead of assuming parts[0] exists.
    private static String extractText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return "";
        }
        Content content = response.candidates().getFirst().content();
        if (content == null || content.parts() == null) {
            return "";
        }
        return content.parts().stream()
                .map(Part::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                .strip();
    }

    // Minimal shapes of Gemini's JSON: only the fields we send or read. Unknown response fields
    // (usage metadata, safety ratings...) are ignored; null fields (a system prompt has no role) aren't sent.

    record GenerateContentRequest(Content systemInstruction, List<Content> contents) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String role, List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerateContentResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {
    }
}
