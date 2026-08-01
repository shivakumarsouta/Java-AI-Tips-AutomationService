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
            You are an expert Java Compiler Engineer, Principal Architect, and elite Technical Interviewer.
            Your task is to write a highly detailed, professional, and comprehensive Java Interview Tip.
            You must output your response ONLY in JSON format matching this schema exactly:
            {
              "title": "A catchy, precise topic title. Keep it clean without markdown symbols.",
              "summary": "A 1-2 sentence concise executive summary of the concept's core importance.",
              "detailedHtml": "Detailed technical explanation. Use clean HTML tags: <p>, <ul>, <li>, <strong>, <em>. For code blocks, you MUST wrap code in <pre><code class=\\"language-java\\">...</code></pre>. Use spaces/tabs correctly inside code. Explain core mechanics, memory layout if applicable, runtime behavior, and critical JVM tuning flags or optimization details. Include 1-2 common interviewer gotchas/questions at the end."
            }
            Do not wrap your JSON in markdown code blocks like ```json ... ```. Output raw JSON.
            """;

        String userPrompt = String.format(
            "Generate a master-level Java interview tip about the following concept: '%s'. " +
            "Provide a production-quality, copy-pasteable Java code snippet illustrating best practices or demonstrating the mechanism. " +
            "Contrast it with old/incorrect ways if relevant.",
            concept
        );

        // Construct request payload
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", this.apiModel);
        requestBody.put("temperature", 0.7);
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
