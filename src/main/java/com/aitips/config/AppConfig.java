package com.aitips.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Handles application configuration loading, variable interpolation, env overrides, and validation.
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    
    private final Properties properties = new Properties();

    public AppConfig(String configFilePath) {
        loadProperties(configFilePath);
    }

    private void loadProperties(String pathStr) {
        // 1. Try local file path if provided, or default to "config.properties" in the working directory
        Path localPath = Paths.get(pathStr != null ? pathStr : "config.properties");
        if (Files.exists(localPath)) {
            logger.info("Loading configuration from local file: {}", localPath.toAbsolutePath());
            try (InputStream input = new FileInputStream(localPath.toFile())) {
                properties.load(input);
            } catch (IOException e) {
                logger.error("Failed to load configuration file at {}", localPath, e);
            }
        } else {
            // 2. Try classpath
            logger.debug("Local configuration file not found at {}. Searching classpath.", localPath);
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (input != null) {
                    properties.load(input);
                    logger.info("Loaded configuration from classpath resource 'config.properties'");
                } else {
                    logger.warn("No 'config.properties' found on classpath or filesystem. Relying entirely on environment/system properties.");
                }
            } catch (IOException e) {
                logger.error("Failed to load configuration from classpath", e);
            }
        }
    }

    /**
     * Resolves a key. Order of precedence:
     * 1. Environment variables (e.g. LLM_API_KEY for llm.api.key)
     * 2. System properties (-Dllm.api.key=...)
     * 3. properties file
     */
    public String get(String key) {
        // Convert key (e.g. llm.api.key) to environment variable style (e.g. LLM_API_KEY)
        String envKey = key.replace('.', '_').toUpperCase();
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.trim().isEmpty()) {
            return envVal;
        }
        
        // Check System properties
        String sysVal = System.getProperty(key);
        if (sysVal != null && !sysVal.trim().isEmpty()) {
            return sysVal;
        }

        return properties.getProperty(key);
    }

    public String getRequired(String key) {
        String val = get(key);
        if (val == null || val.trim().isEmpty()) {
            String envKey = key.replace('.', '_').toUpperCase();
            throw new IllegalStateException(
                String.format("Required configuration property '%s' is missing. Add it to config.properties or set environment variable '%s'.", key, envKey)
            );
        }
        return val.trim();
    }

    public String getOrDefault(String key, String defaultVal) {
        String val = get(key);
        return (val == null || val.trim().isEmpty()) ? defaultVal : val.trim();
    }

    public int getIntOrDefault(String key, int defaultVal) {
        String val = get(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for key '{}': '{}'. Using default: {}", key, val, defaultVal);
            return defaultVal;
        }
    }

    public boolean getBooleanOrDefault(String key, boolean defaultVal) {
        String val = get(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultVal;
        }
        return Boolean.parseBoolean(val.trim());
    }

    /**
     * Validates that all required configuration settings are present.
     */
    public void validate() {
        getRequired("llm.api.url");
        getRequired("llm.api.key");
        getRequired("llm.api.model");
        
        getRequired("smtp.host");
        getRequired("smtp.username");
        getRequired("smtp.password");
        getRequired("smtp.from");
        getRequired("smtp.to");

        getRequired("schedule.time");
        getRequired("db.url");
        
        logger.info("Configuration validation completed successfully.");
    }
}
