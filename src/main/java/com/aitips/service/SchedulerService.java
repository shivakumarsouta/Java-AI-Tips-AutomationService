package com.aitips.service;

import com.aitips.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles scheduling of the daily tip generator task using ZonedDateTime to ensure correct local-time execution.
 */
public class SchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    private final TipGeneratorService tipGeneratorService;
    private final ScheduledExecutorService scheduler;
    private final int targetHour;
    private final int targetMinute;
    private final ZoneId zoneId;
    private ScheduledFuture<?> scheduledFuture;
    private boolean running = false;

    public SchedulerService(AppConfig config, TipGeneratorService tipGeneratorService) {
        this.tipGeneratorService = tipGeneratorService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tip-scheduler-thread");
            thread.setDaemon(true); // Daemon threads allow the JVM to exit if the main application stops
            return thread;
        });

        // Parse scheduling target time
        String timeStr = config.getOrDefault("schedule.time", "08:00");
        try {
            String[] parts = timeStr.split(":");
            this.targetHour = Integer.parseInt(parts[0]);
            this.targetMinute = Integer.parseInt(parts[1]);
            if (targetHour < 0 || targetHour > 23 || targetMinute < 0 || targetMinute > 59) {
                throw new IllegalArgumentException("Hour must be 0-23 and minute must be 0-59.");
            }
        } catch (Exception e) {
            logger.error("Failed to parse schedule.time '{}'. Must be in format HH:mm. Defaulting to 08:00.", timeStr);
            throw new IllegalArgumentException("Invalid schedule time: " + timeStr, e);
        }

        // Parse timezone
        String timezoneStr = config.getOrDefault("schedule.timezone", "UTC");
        ZoneId parsedZone;
        try {
            parsedZone = ZoneId.of(timezoneStr);
        } catch (Exception e) {
            logger.warn("Invalid schedule.timezone '{}'. Falling back to UTC.", timezoneStr);
            parsedZone = ZoneId.of("UTC");
        }
        this.zoneId = parsedZone;

        logger.info("Scheduler initialized. Daily target time: {:02d}:{:02d} (Timezone: {})", 
                targetHour, targetMinute, zoneId.getId());
    }

    /**
     * Starts the scheduled background execution cycle.
     */
    public synchronized void start() {
        if (running) {
            logger.warn("Scheduler is already running.");
            return;
        }
        running = true;
        logger.info("Starting background scheduler service...");
        scheduleNextRun();
    }

    private synchronized void scheduleNextRun() {
        if (!running) {
            return;
        }

        long delayMs = calculateDelayToNextRun();
        long hours = delayMs / 3600000;
        long minutes = (delayMs % 3600000) / 60000;
        long seconds = (delayMs % 60000) / 1000;

        logger.info("Scheduling next execution in {} ms (approx. {}h {}m {}s)", 
                delayMs, hours, minutes, seconds);

        scheduledFuture = scheduler.schedule(this::executeAndReschedule, delayMs, TimeUnit.MILLISECONDS);
    }

    private void executeAndReschedule() {
        try {
            logger.info("Scheduled time reached. Triggering tip generator execution...");
            tipGeneratorService.runDailyTask();
        } catch (Exception e) {
            logger.error("Unhandled error inside scheduled execution runner", e);
        } finally {
            // Recalculate and reschedule next execution (ensuring continuous execution loop)
            scheduleNextRun();
        }
    }

    private long calculateDelayToNextRun() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime nextRun = now.withHour(targetHour)
                                    .withMinute(targetMinute)
                                    .withSecond(0)
                                    .withNano(0);
        
        // If target time has already passed today, target the same time tomorrow
        if (now.isAfter(nextRun) || now.isEqual(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        
        return Duration.between(now, nextRun).toMillis();
    }

    /**
     * Gracefully stops the scheduler and releases resources.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        logger.info("Stopping scheduler service...");

        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warn("Forced scheduler shutdown completed.");
            } else {
                logger.info("Scheduler shutdown completed cleanly.");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
