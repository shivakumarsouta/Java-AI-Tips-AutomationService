package com.aitips;

import com.aitips.config.AppConfig;
import com.aitips.db.DatabaseManager;
import com.aitips.llm.LlmClient;
import com.aitips.mail.EmailSender;
import com.aitips.service.SchedulerService;
import com.aitips.service.TipGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Main application entry point responsible for bootstrapping, CLI parsing, dependency assembly,
 * and lifecycle management.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        logger.info("Initializing Java Interview Tip Automation Service...");

        boolean dryRun = false;
        String configPath = "config.properties";

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--dry-run".equalsIgnoreCase(arg) || "-d".equalsIgnoreCase(arg)) {
                dryRun = true;
            } else if ("--config".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                if (i + 1 < args.length) {
                    configPath = args[++i];
                } else {
                    logger.error("Command line error: --config requires a file path argument.");
                    printUsage();
                    System.exit(1);
                }
            } else if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                printUsage();
                System.exit(0);
            }
        }

        DatabaseManager dbManager = null;
        SchedulerService schedulerService = null;

        try {
            // 1. Initialize configurations
            AppConfig config = new AppConfig(configPath);
            config.validate();

            // 2. Instantiate component dependencies
            dbManager = new DatabaseManager(config.getRequired("db.url"));
            LlmClient llmClient = new LlmClient(config);
            EmailSender emailSender = new EmailSender(config);

            // 3. Assemble application orchestrator
            TipGeneratorService tipGenerator = new TipGeneratorService(dbManager, llmClient, emailSender);

            // 4. Handle execution modes
            if (dryRun) {
                logger.info("Running in DRY-RUN mode. Executing tip automation task immediately...");
                tipGenerator.runDailyTask();
                logger.info("Dry-run process completed successfully. Exiting.");
                
                // Clean up database resources and exit
                dbManager.close();
                System.exit(0);
            }

            // Standard Scheduled Execution Mode
            schedulerService = new SchedulerService(config, tipGenerator);
            schedulerService.start();

            final DatabaseManager finalDb = dbManager;
            final SchedulerService finalSched = schedulerService;

            // Setup runtime JVM shutdown hooks for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Termination signal received. Gracefully stopping daemon threads...");
                try {
                    finalSched.stop();
                    finalDb.close();
                } catch (Exception e) {
                    logger.error("Error during graceful shutdown execution", e);
                } finally {
                    shutdownLatch.countDown();
                    logger.info("Service cleanup complete. Exiting JVM.");
                }
            }, "shutdown-cleanup-thread"));

            logger.info("Service is successfully running in scheduled loop mode. Press Ctrl+C to terminate.");

            // Keep main execution thread blocked while scheduler runs in background
            try {
                shutdownLatch.await();
            } catch (InterruptedException e) {
                logger.warn("Scheduler main wait thread interrupted.", e);
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            logger.error("A critical startup exception occurred. Service execution aborted.", e);
            if (dbManager != null) {
                dbManager.close();
            }
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("AI-Powered Daily Java Interview Tip Automation Service");
        System.out.println("Usage: java -jar java-interview-tips.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d, --dry-run         Executes the daily process immediately once and exits.");
        System.out.println("  -c, --config <path>   Specifies a custom config.properties file location.");
        System.out.println("  -h, --help            Displays this help message.");
    }
}
