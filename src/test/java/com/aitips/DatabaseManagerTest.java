package com.aitips;

import com.aitips.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseManager verifying schema execution, state retrieval, insertions, and history clears.
 */
public class DatabaseManagerTest {
    private DatabaseManager dbManager;

    @BeforeEach
    public void setUp() {
        // Use an in-memory SQLite database instance for clean, isolated test runs
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
    }

    @AfterEach
    public void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    public void testDatabaseFlowAndDeduplication() {
        // 1. Initial State assertions
        assertFalse(dbManager.hasConceptBeenSent("Java Streams"));
        assertTrue(dbManager.getSentConcepts().isEmpty());

        // 2. Insert record and assert
        dbManager.recordSentTip("Java Streams", "Streams Tip", "<p>Tip Content</p>");
        assertTrue(dbManager.hasConceptBeenSent("Java Streams"));
        
        Set<String> sent = dbManager.getSentConcepts();
        assertEquals(1, sent.size());
        assertTrue(sent.contains("Java Streams"));

        // 3. Second insert
        dbManager.recordSentTip("Virtual Threads", "Lightweight threads", "<p>Thread Content</p>");
        assertTrue(dbManager.hasConceptBeenSent("Virtual Threads"));
        assertEquals(2, dbManager.getSentConcepts().size());

        // 4. Overwrite record (Idempotency check)
        dbManager.recordSentTip("Java Streams", "Streams Tip Updated", "<p>Updated Content</p>");
        assertEquals(2, dbManager.getSentConcepts().size());

        // 5. Clear history
        dbManager.clearHistory();
        assertFalse(dbManager.hasConceptBeenSent("Java Streams"));
        assertFalse(dbManager.hasConceptBeenSent("Virtual Threads"));
        assertTrue(dbManager.getSentConcepts().isEmpty());
    }
}
