package com.aitips;

import com.aitips.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for checking config fallback behavior, integer parsing, and validation triggers.
 */
public class AppConfigTest {

    @Test
    public void testConfigurationDefaultsAndExceptions() {
        // Instantiate using a non-existent file to force fallback to defaults/classpath search
        AppConfig config = new AppConfig("missing-file-name.properties");

        // Verify fallback defaults
        assertEquals("08:00", config.getOrDefault("schedule.time", "08:00"));
        assertEquals("UTC", config.getOrDefault("schedule.timezone", "UTC"));
        assertEquals(30, config.getIntOrDefault("llm.api.timeout", 30));
        assertTrue(config.getBooleanOrDefault("smtp.auth", true));
        assertFalse(config.getBooleanOrDefault("smtp.starttls", false)); // custom default test

        // Verify validation exceptions on missing required keys
        assertThrows(IllegalStateException.class, () -> config.getRequired("missing.property.key"));
    }
}
