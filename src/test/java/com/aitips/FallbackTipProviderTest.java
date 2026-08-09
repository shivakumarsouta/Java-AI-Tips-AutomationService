package com.aitips;

import com.aitips.llm.FallbackTipProvider;
import com.aitips.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying FallbackTipProvider returns structured tips for cataloged and generic topics.
 */
public class FallbackTipProviderTest {

    @Test
    public void testFallbackProviderCatalogAndGeneric() {
        FallbackTipProvider provider = new FallbackTipProvider();

        // 1. Test specific cataloged topic
        LlmClient.GeneratedTip tip = provider.getFallbackTip("Java Garbage Collectors (G1 vs ZGC differences and use cases)");
        assertNotNull(tip);
        assertNotNull(tip.title);
        assertTrue(tip.title.contains("Garbage Collectors"));
        assertNotNull(tip.detailedHtml);

        // 2. Test generic fallback generation for uncataloged topics
        LlmClient.GeneratedTip genericTip = provider.getFallbackTip("Custom Topic Test");
        assertNotNull(genericTip);
        assertTrue(genericTip.title.contains("Custom Topic Test"));
        assertTrue(genericTip.detailedHtml.contains("Custom Topic Test"));
    }
}
