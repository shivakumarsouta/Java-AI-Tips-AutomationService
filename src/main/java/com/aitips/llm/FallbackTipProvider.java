package com.aitips.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Resiliency fallback provider supplying pre-formatted high quality Java interview tips
 * when the external LLM API is unreachable or rate-limited.
 */
public class FallbackTipProvider {
    private static final Logger logger = LoggerFactory.getLogger(FallbackTipProvider.class);

    private final Map<String, LlmClient.GeneratedTip> fallbackCatalog = new HashMap<>();

    public FallbackTipProvider() {
        populateCatalog();
    }

    private void populateCatalog() {
        // Fallback 1: Garbage Collectors
        LlmClient.GeneratedTip gcTip = new LlmClient.GeneratedTip();
        gcTip.title = "Java Garbage Collectors: G1 vs ZGC Differences and Use Cases";
        gcTip.summary = "G1 GC is designed for multi-gigabyte heaps with balanced throughput and pause target predictability, whereas ZGC provides sub-millisecond max pause times for multi-terabyte heaps.";
        gcTip.detailedHtml = """
            <p><strong>Overview:</strong> The JVM provides specialized garbage collectors tailored to different latency and throughput requirements. G1 (Garbage-First) is the default collector since Java 9, while ZGC (Z Garbage Collector) is a low-latency collector introduced in Java 15+.</p>
            <p><strong>Core Mechanics:</strong></p>
            <ul>
              <li><strong>G1 GC:</strong> Divides the heap into equal-sized regions (1MB to 32MB). It uses concurrent marking and incremental compaction targeting a configurable pause goal (<code>-XX:MaxGCPauseMillis</code>).</li>
              <li><strong>ZGC:</strong> Employs colored pointers and load barriers to perform nearly all GC phases concurrently with application threads, achieving sub-millisecond pause times regardless of heap size.</li>
            </ul>
            <pre><code class="language-java">// Example JVM flags for selecting Garbage Collectors
// Enable G1 GC (Default in JDK 9+):
// java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar app.jar

// Enable ZGC (JDK 15+):
// java -XX:+UseZGC -XX:+ZGenerational -jar app.jar
</code></pre>
            <p><strong>Interviewer Gotchas:</strong> ZGC trades slightly lower single-thread throughput for ultra-low pause latency. If throughput is the primary metric over pause latency, G1 or Parallel GC may be preferable.</p>
            """;
        fallbackCatalog.put("Java Garbage Collectors (G1 vs ZGC differences and use cases)", gcTip);

        // Fallback 2: Virtual Threads
        LlmClient.GeneratedTip vtTip = new LlmClient.GeneratedTip();
        vtTip.title = "Virtual Threads (Project Loom) in Java 21";
        vtTip.summary = "Virtual threads are lightweight, JVM-managed threads designed to dramatically reduce the cost of high-throughput I/O-bound concurrency.";
        vtTip.detailedHtml = """
            <p><strong>Overview:</strong> Traditional OS platform threads are expensive (~1MB stack per thread). Virtual threads (Java 21) decouple Java threads from OS threads, allowing millions of concurrent tasks to execute simultaneously.</p>
            <p><strong>Key Gotchas:</strong> Avoid pinning virtual threads to platform carrier threads by replacing <code>synchronized</code> blocks with <code>ReentrantLock</code> during blocking I/O calls.</p>
            <pre><code class="language-java">try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        final int taskId = i;
        executor.submit(() -> {
            // High throughput blocking network call
            java.util.concurrent.TimeUnit.MILLISECONDS.sleep(100);
            return taskId;
        });
    }
} // AutoCloseable executor awaits completion
</code></pre>
            """;
        fallbackCatalog.put("Virtual Threads (Project Loom, how they differ from Platform Threads)", vtTip);
    }

    /**
     * Obtains a fallback tip for the given concept.
     */
    public LlmClient.GeneratedTip getFallbackTip(String concept) {
        logger.info("Retrieving resilience fallback tip for concept: '{}'", concept);
        LlmClient.GeneratedTip exactMatch = fallbackCatalog.get(concept);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Generic fallback if concept isn't explicitly cataloged
        LlmClient.GeneratedTip genericTip = new LlmClient.GeneratedTip();
        genericTip.title = "Java Interview Prep: " + concept;
        genericTip.summary = "Essential architectural concepts and JVM best practices for " + concept + ".";
        genericTip.detailedHtml = String.format("""
            <p><strong>Concept Deep-Dive:</strong> %s</p>
            <p>Understanding the internal mechanisms, thread-safety guarantees, and memory layout of <em>%s</em> is essential for senior technical interviews.</p>
            <pre><code class="language-java">// Production Best Practice snippet for %s
public final class ConceptDemo {
    public static void execute() {
        System.out.println("Demonstrating best practice mechanics for: " + "%s");
    }
}
</code></pre>
            <p><strong>Key takeaway:</strong> Always analyze memory overhead, synchronization boundaries, and garbage collection implications when applying this concept in production.</p>
            """, concept, concept, concept, concept);
        return genericTip;
    }
}
