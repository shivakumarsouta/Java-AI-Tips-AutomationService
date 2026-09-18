package com.aitips.llm;

import com.aitips.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Communicates with the LLM API (OpenAI/Groq compatible) to generate structured interview tips.
 */
public class LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiUrl;
    private final String apiKey;
    private final String apiModel;
    private final Duration timeout;
    private final HttpClient httpClient;

    public LlmClient(AppConfig config) {
        this.apiUrl = config.getRequired("llm.api.url");
        this.apiKey = config.getRequired("llm.api.key");
        this.apiModel = config.getRequired("llm.api.model");
        int timeoutSec = config.getIntOrDefault("llm.api.timeout", 30);
        this.timeout = Duration.ofSeconds(timeoutSec);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    public static class GeneratedTip {
        public String title;
        public String summary;
        public String detailedHtml;

        @Override
        public String toString() {
            return "GeneratedTip{" +
                    "title='" + title + '\'' +
                    ", summary='" + summary + '\'' +
                    '}';
        }
    }

    /**
     * Generates an interview tip for a given Java concept with 3 retry attempts.
     */
    public GeneratedTip generateTip(String concept) {
        int maxRetries = 3;
        int delaySeconds = 2;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return generateTipInternal(concept);
            } catch (Exception e) {
                logger.warn("LLM generation attempt {}/{} failed for concept '{}': {}", 
                        attempt, maxRetries, concept, e.getMessage());
                lastException = e;
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep((long) delaySeconds * attempt * 1000); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM generation interrupted", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Failed to generate tip after " + maxRetries + " attempts", lastException);
    }

    private GeneratedTip generateTipInternal(String concept) throws IOException, InterruptedException {
        String systemPrompt = """
            You are a Principal Java Architect and Technical Interviewer.
            Your task is to write a complete, focused, self-contained Java Engineering & Interview Tip on 1 core concept.
            CRITICAL REQUIREMENT: Do NOT output incomplete or truncated sentences/code. Ensure all code blocks, HTML tags, and Q&As are completely finished.

            You must output your response ONLY in JSON format matching this schema exactly:
            {
              "title": "A precise, professional topic title. Clean text without markdown symbols.",
              "summary": "A 2-sentence executive summary explaining the core concept and its production importance.",
              "detailedHtml": "HTML string containing a focused technical breakdown organized into 4 explicit sections:

              <h3>1. Core Mechanics & Internal Architecture</h3>
              <p>Granular, step-by-step technical explanation covering JVM internals, memory behavior, or framework mechanics for this concept.</p>

              <h3>2. Production-Grade Java Implementation</h3>
              <p>A concise, idiomatic modern Java code snippet (15-25 lines max) demonstrating best practices.</p>
              <pre><code class=\\"language-java\\">// Focused, production-ready Java code example</code></pre>

              <h3>3. Performance, Memory & JVM Tuning Gotchas</h3>
              <p>Key latency risks, memory implications, or critical JVM/framework tuning flags.</p>

              <h3>4. Top Interviewer Trap Questions & Deep Answers</h3>
              <p>1-2 high-impact interview questions with complete, definitive answers. Always finish every sentence completely.</p>

              Use clean HTML tags: <h3>, <p>, <ul>, <ol>, <li>, <strong>, <em>, <code>. Wrap Java code blocks strictly in <pre><code class=\\"language-java\\">...</code></pre>."
            }
            Do not wrap JSON in markdown code blocks. Output raw JSON.
            """;

        String userPrompt = String.format(
            "Generate a clear, focused, complete Java technical tip for the concept: '%s'. " +
            "Focus on 1 core concept in depth. Keep the Java code snippet concise (15-25 lines max), and provide 1-2 complete interviewer Q&As without truncation.",
            concept
        );

        // Construct request payload
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", this.apiModel);
        requestBody.put("temperature", 0.6);
        requestBody.put("max_tokens", 4096);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + this.apiKey)
                .timeout(this.timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        logger.info("Sending request to LLM API for concept: '{}' (Model: {})", concept, this.apiModel);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("LLM API returned non-OK status: {}. Response: {}", response.statusCode(), response.body());
            throw new IOException("LLM API error (status " + response.statusCode() + "): " + response.body());
        }

        JsonNode rootNode = objectMapper.readTree(response.body());
        JsonNode choiceNode = rootNode.path("choices").get(0);
        if (choiceNode == null || choiceNode.isMissingNode()) {
            throw new IOException("Invalid LLM response structure: choices list is missing.");
        }

        String finishReason = choiceNode.path("finish_reason").asText("");
        if ("length".equalsIgnoreCase(finishReason)) {
            logger.warn("LLM API output was truncated due to token limit (finish_reason=length) for concept: '{}'", concept);
            throw new IOException("LLM output was truncated due to max token limit (finish_reason=length).");
        }

        String contentString = choiceNode.path("message").path("content").asText();
        if (contentString == null || contentString.trim().isEmpty()) {
            throw new IOException("Empty response message content from LLM.");
        }

        logger.debug("Raw JSON content received from LLM: {}", contentString);

        // Parse the nested JSON structure returned by the assistant
        JsonNode tipNode = objectMapper.readTree(contentString);
        GeneratedTip tip = new GeneratedTip();
        tip.title = tipNode.path("title").asText("Java Interview Tip");
        tip.summary = tipNode.path("summary").asText("");
        tip.detailedHtml = tipNode.path("detailedHtml").asText("");

        if (tip.detailedHtml.isEmpty()) {
            throw new IOException("Parsed tip detailedHtml is empty or missing from response.");
        }

        return tip;
    }
}
