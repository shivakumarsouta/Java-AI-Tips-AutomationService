package com.aitips;

import com.aitips.db.DatabaseManager;
import com.aitips.service.ArchiveGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying ArchiveGenerator generates docs/index.html containing SQLite records.
 */
public class ArchiveGeneratorTest {
    private DatabaseManager dbManager;

    @BeforeEach
    public void setUp() {
        dbManager = new DatabaseManager("jdbc:sqlite::memory:");
    }

    @AfterEach
    public void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    public void testArchiveGeneratorExecution() throws Exception {
        dbManager.recordSentTip("Concept A", "Title A", "<p>Content A</p>");
        dbManager.recordSentTip("Concept B", "Title B", "<p>Content B</p>");

        ArchiveGenerator generator = new ArchiveGenerator(dbManager);
        generator.generateArchive();

        Path indexPath = Paths.get("docs", "index.html");
        assertTrue(Files.exists(indexPath), "docs/index.html should exist after archive generation.");

        String content = Files.readString(indexPath);
        assertTrue(content.contains("Title A"));
        assertTrue(content.contains("Title B"));
    }
}
