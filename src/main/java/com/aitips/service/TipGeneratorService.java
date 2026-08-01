package com.aitips.service;

import com.aitips.db.DatabaseManager;
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
 * Orchestrates the full daily cycle: loading concepts, picking next unused, generating via LLM,
 * emailing, and persisting tracking state.
 */
public class TipGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(TipGeneratorService.class);

    private final DatabaseManager dbManager;
    private final LlmClient llmClient;
    private final EmailSender emailSender;

    public TipGeneratorService(DatabaseManager dbManager, LlmClient llmClient, EmailSender emailSender) {
        this.dbManager = dbManager;
        this.llmClient = llmClient;
        this.emailSender = emailSender;
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

            // 4. Exhaustion check: if all concepts have been used, reset database tracking
            if (unusedConcepts.isEmpty()) {
                logger.warn("All seed concepts have been exhausted! Resetting database history to start over.");
                dbManager.clearHistory();
                unusedConcepts = new ArrayList<>(allConcepts);
            }

            logger.info("{} unused concepts are available for selection.", unusedConcepts.size());

            // 5. Select the first unused concept sequentially
            String selectedConcept = unusedConcepts.get(0);
            logger.info("Selected concept: '{}'", selectedConcept);

            // 6. Request the LLM to generate the tip details
            LlmClient.GeneratedTip tip = llmClient.generateTip(selectedConcept);
            logger.info("Successfully generated tip content for: '{}' (Title: '{}')", selectedConcept, tip.title);

            // 7. Send the styled email
            emailSender.sendTipEmail(tip.title, tip.summary, tip.detailedHtml);

            // 8. Log the successfully sent tip in the database
            dbManager.recordSentTip(selectedConcept, tip.title, tip.detailedHtml);
            logger.info("Daily Java interview tip execution completed successfully.");

        } catch (Exception e) {
            logger.error("Critical failure during daily Java interview tip execution task", e);
        }
    }

    /**
     * Loads the list of topics from a local seed_concepts.txt file first, and falls back to classpath resource if not found.
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

        // 2. Fall back to embedded classpath resource if local is empty or non-existent
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
