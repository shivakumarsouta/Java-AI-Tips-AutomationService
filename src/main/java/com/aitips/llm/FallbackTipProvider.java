package com.aitips.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides pre-written fallback tips from a local JSON file when the LLM API is unavailable.
 * This implements the Circuit Breaker pattern to ensure the daily email is always delivered.
 */
public class FallbackTipProvider {
    private static final Logger logger = LoggerFactory.getLogger(FallbackTipProvider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String FALLBACK_RESOURCE = "fallback_tips.json";

    private final List<LlmClient.GeneratedTip> fallbackTips = new ArrayList<>();

    public FallbackTipProvider() {
        loadFallbackTips();
    }

    private void loadFallbackTips() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(FALLBACK_RESOURCE)) {
            if (input == null) {
                logger.warn("Fallback tips resource '{}' not found on classpath.", FALLBACK_RESOURCE);
                return;
            }
            JsonNode rootArray = objectMapper.readTree(input);
            if (!rootArray.isArray()) {
                logger.error("fallback_tips.json is not a valid JSON array.");
                return;
            }
            for (JsonNode node : rootArray) {
                LlmClient.GeneratedTip tip = new LlmClient.GeneratedTip();
                tip.title = node.path("title").asText();
                tip.summary = node.path("summary").asText();
                tip.detailedHtml = node.path("detailedHtml").asText();
                fallbackTips.add(tip);
            }
            logger.info("Loaded {} fallback tips from '{}'.", fallbackTips.size(), FALLBACK_RESOURCE);
        } catch (IOException e) {
            logger.error("Failed to load fallback tips from classpath resource.", e);
        }
    }

    /**
     * Returns a fallback tip by cycling through the list using the day-of-year as an index.
     * This ensures different tips rotate rather than always returning the same one.
     */
    public LlmClient.GeneratedTip getNextFallbackTip() {
        if (fallbackTips.isEmpty()) {
            logger.error("No fallback tips available. Returning a minimal hardcoded tip.");
            LlmClient.GeneratedTip emergency = new LlmClient.GeneratedTip();
            emergency.title = "Java Best Practice: Always Prefer Interfaces Over Concrete Classes";
            emergency.summary = "Programming to interfaces instead of implementations is a core design principle that makes your code flexible and testable.";
            emergency.detailedHtml = "<p>When declaring variables and method parameters, always use the interface type rather than the concrete class. For example, use <code>List&lt;String&gt;</code> instead of <code>ArrayList&lt;String&gt;</code>. This allows you to swap implementations without changing other code.</p>";
            return emergency;
        }
        // Use day-of-year modulo list size to cycle through tips
        int dayOfYear = java.time.LocalDate.now().getDayOfYear();
        int index = dayOfYear % fallbackTips.size();
        LlmClient.GeneratedTip tip = fallbackTips.get(index);
        logger.info("Using fallback tip at index {}: '{}'", index, tip.title);
        return tip;
    }

    public boolean hasFallbackTips() {
        return !fallbackTips.isEmpty();
    }
}
