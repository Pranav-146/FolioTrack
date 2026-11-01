package com.foliotrack.llm;

// The rest of the app only knows "text in, text out". The provider (Gemini today) is an implementation detail
// picked by configuration, and tests replace this interface with a Mockito mock instead of calling a real API.
public interface LlmClient {

    /**
     * Sends one prompt and returns the model's text reply.
     *
     * @throws com.foliotrack.exception.LlmUnavailableException if the provider can't be reached or gives no answer
     */
    String complete(String systemPrompt, String userMessage);
}
