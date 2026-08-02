package com.aitips.service;

import com.aitips.db.DatabaseManager;
import com.aitips.llm.FallbackTipProvider;
import com.aitips.llm.LlmClient;
import com.aitips.mail.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates the full daily cycle: concept selection, LLM generation (with fallback),
 * email delivery, state persistence, and archive website generation.
 */
public class TipGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(TipGeneratorService.class);

    private final DatabaseManager dbManager;
    private final LlmClient llmClient;
    private final EmailSender emailSender;
    private final FallbackTipProvider fallbackProvider;
    private final ArchiveGenerator archiveGenerator;

    public TipGeneratorService(DatabaseManager dbManager, LlmClient llmClient, EmailSender emailSender) {
        this.dbManager = dbManager;
        this.llmClient = llmClient;
        this.emailSender = emailSender;
        this.fallbackProvider = new FallbackTipProvider();
        this.archiveGenerator = new ArchiveGenerator(dbManager);
    }

    /**
     * Executes the daily interview tip workflow.
     */
    public synchronized void runDailyTask() {
        logger.info("Executing daily Java interview tip task cycle...");
        try {
            // 1. Load all seed concepts
            List<String> allConcepts = loadSeedConcepts();
            if (allConcepts.isEmpty()) {
                logger.error("No seed concepts loaded. Aborting tip automation task.");
                return;
            }
            logger.info("Loaded {} total seed concepts.", allConcepts.size());

            // 2. Query sent concepts from the database
            Set<String> sentConcepts = dbManager.getSentConcepts();
            logger.info("{} concepts have been sent previously.", sentConcepts.size());

            // 3. Filter out used concepts
            List<String> unusedConcepts = allConcepts.stream()
                    .filter(concept -> !sentConcepts.contains(concept))
                    .collect(Collectors.toList());

            // 4. Exhaustion check: reset if all concepts have been used
            if (unusedConcepts.isEmpty()) {
                logger.warn("All seed concepts have been exhausted! Resetting database history to start over.");
                dbManager.clearHistory();
                unusedConcepts = new ArrayList<>(allConcepts);
            }

            logger.info("{} unused concepts available for selection.", unusedConcepts.size());

            // 5. Select the first unused concept sequentially
            String selectedConcept = unusedConcepts.get(0);
            logger.info("Selected concept: '{}'", selectedConcept);

            // 6. Generate tip — with fallback resiliency if LLM fails
            LlmClient.GeneratedTip tip = generateWithFallback(selectedConcept);
            logger.info("Tip ready: '{}'", tip.title);

            // 7. Send the styled email
            emailSender.sendTipEmail(tip.title, tip.summary, tip.detailedHtml);

            // 8. Record the sent tip in the database
            dbManager.recordSentTip(selectedConcept, tip.title, tip.detailedHtml);
            logger.info("Daily Java interview tip execution completed successfully.");

            // 9. Regenerate the archive website with the updated database
            archiveGenerator.generateArchive();

        } catch (Exception e) {
            logger.error("Critical failure during daily Java interview tip execution task", e);
        }
    }

    /**
     * Attempts LLM generation first. If it fails after all retries, uses a pre-written fallback tip.
     * This is the Circuit Breaker pattern — the service always delivers something.
     */
    private LlmClient.GeneratedTip generateWithFallback(String concept) {
        if (llmClient == null) {
            logger.warn("LLM client is not configured. Using fallback tip directly.");
            return fallbackProvider.getNextFallbackTip();
        }
        try {
            return llmClient.generateTip(concept);
        } catch (Exception e) {
            logger.error("LLM generation failed for concept '{}' after all retries. Activating fallback.", concept, e);
            if (fallbackProvider.hasFallbackTips()) {
                logger.info("Circuit breaker activated: serving pre-written fallback tip.");
                return fallbackProvider.getNextFallbackTip();
            }
            throw new RuntimeException("LLM failed and no fallback tips are available.", e);
        }
    }

    /**
     * Loads seed concepts from a local seed_concepts.txt file first,
     * falling back to the classpath resource if not found.
     */
    public List<String> loadSeedConcepts() {
        List<String> concepts = new ArrayList<>();

        // 1. Check local filesystem path first
        Path localFile = Paths.get("seed_concepts.txt");
        if (Files.exists(localFile)) {
            logger.info("Loading seed concepts from local file system: {}", localFile.toAbsolutePath());
            try (BufferedReader reader = Files.newBufferedReader(localFile, StandardCharsets.UTF_8)) {
                readLines(reader, concepts);
            } catch (IOException e) {
                logger.error("Failed to read local seed_concepts.txt file", e);
            }
        }

        // 2. Fall back to embedded classpath resource
        if (concepts.isEmpty()) {
            logger.debug("Local seed_concepts.txt empty/not found. Checking classpath resource.");
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("seed_concepts.txt")) {
                if (input != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                        readLines(reader, concepts);
                        logger.info("Loaded {} seed concepts from classpath resource.", concepts.size());
                    }
                } else {
                    logger.error("Resource 'seed_concepts.txt' not found on the classpath.");
                }
            } catch (IOException e) {
                logger.error("Error reading seed_concepts.txt resource from classpath", e);
            }
        }

        return concepts;
    }

    private void readLines(BufferedReader reader, List<String> targetList) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                targetList.add(trimmed);
            }
        }
    }
}
