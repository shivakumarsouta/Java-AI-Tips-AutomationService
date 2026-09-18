package com.aitips;

import com.aitips.db.DatabaseManager;
import com.aitips.service.TipGeneratorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying TipGeneratorService's logic for parsing resources and loading seed concepts.
 */
public class TipGeneratorServiceTest {

    @Test
    public void testSeedConceptsLoading() {
        DatabaseManager db = new DatabaseManager("jdbc:sqlite::memory:");
        try {
            TipGeneratorService service = new TipGeneratorService(db, null, null);
            List<String> concepts = service.loadSeedConcepts();

            assertNotNull(concepts);
            assertFalse(concepts.isEmpty(), "Concepts list must have loaded topics from classpath resource.");
            
            // Check that the comments and empty lines are filtered
            boolean hasInvalidLine = concepts.stream().anyMatch(c -> c.trim().isEmpty() || c.startsWith("#"));
            assertFalse(hasInvalidLine, "Loaded concepts must not contain empty or commented-out lines.");

            // Confirm key JVM/Java topics and new pillars are loaded
            boolean hasGarbageCollector = concepts.stream().anyMatch(c -> c.contains("Garbage Collectors"));
            assertTrue(hasGarbageCollector, "Topics should contain 'Garbage Collectors'.");

            boolean hasCommonMisconceptions = concepts.stream().anyMatch(c -> c.startsWith("[Common Misconceptions]"));
            assertTrue(hasCommonMisconceptions, "Catalog should contain [Common Misconceptions] topics.");

            boolean hasInterviewCore = concepts.stream().anyMatch(c -> c.startsWith("[Interview Core]"));
            assertTrue(hasInterviewCore, "Catalog should contain [Interview Core] topics.");

            boolean hasFullStackEssentials = concepts.stream().anyMatch(c -> c.startsWith("[Full Stack Essentials]"));
            assertTrue(hasFullStackEssentials, "Catalog should contain [Full Stack Essentials] topics.");
        } finally {
            db.close();
        }
    }
}
