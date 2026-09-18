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
        gcTip.summary = "G1 GC balances throughput and predictable pause targets for multi-gigabyte heaps, whereas ZGC provides sub-millisecond max pause times for multi-terabyte heaps using colored pointers and load barriers.";
        gcTip.detailedHtml = """
            <h3>1. Core Mechanics & Internal Architecture</h3>
            <p>The JVM provides specialized garbage collectors tailored to different latency and throughput requirements. G1 (Garbage-First) is the default collector in JDK 9+, while ZGC (Z Garbage Collector) is an ultra-low latency collector introduced in JDK 15+ and made generational in JDK 21.</p>
            <ul>
              <li><strong>G1 GC:</strong> Divides the heap into equal-sized regions (1MB to 32MB). It tracks region liveness via Remembered Sets (RSets) and performs incremental compaction during mixed GC phases targeting a configurable pause goal (<code>-XX:MaxGCPauseMillis=200</code>).</li>
              <li><strong>ZGC:</strong> Employs 42-bit colored pointers and read/load barriers to perform marking, relocation, and remapping concurrently with application threads, achieving sub-millisecond pause times regardless of heap size (up to 16TB).</li>
            </ul>

            <h3>2. Production-Grade Java Implementation</h3>
            <p>Selecting and tuning garbage collectors in enterprise production environments is accomplished via JVM flags:</p>
            <pre><code class="language-java">// JVM Tuning Flags for Production Deployments

// 1. Production G1 GC Config (Default for balance of throughput & pause targets):
// java -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:InitiatingHeapOccupancyPercent=45 -jar app.jar

// 2. Production Generational ZGC Config (JDK 21+ for ultra-low latency microservices):
// java -XX:+UseZGC -XX:+ZGenerational -Xms16g -Xmx16g -jar app.jar
</code></pre>

            <h3>3. Performance, Memory & JVM Tuning Gotchas</h3>
            <p>ZGC trades slightly lower single-threaded throughput (~3-5% CPU overhead due to load barriers) for ultra-low latency. If raw batch throughput is the primary metric over pause latency, G1 or Parallel GC is preferable. Always set initial heap (<code>-Xms</code>) equal to max heap (<code>-Xmx</code>) in production to avoid dynamic heap expansion overhead.</p>

            <h3>4. Top Interviewer Trap Questions & Deep Answers</h3>
            <p><strong>Q: Why does ZGC require load barriers instead of write barriers?</strong><br>
            <em>Answer:</em> Write barriers trigger when reference fields are modified. ZGC performs object relocation concurrently while application threads read references. Load barriers intercept reference loads from the heap, check the colored pointer bits, and dynamically self-heal the reference to point to the relocated memory address on the fly without stopping application threads.</p>
            """;
        fallbackCatalog.put("Java Garbage Collectors (G1 vs ZGC differences and use cases)", gcTip);
        fallbackCatalog.put("[Interview Core] Java Garbage Collectors (G1 vs ZGC differences, generational ZGC, pause targets, and memory region layouts)", gcTip);

        // Fallback 2: Virtual Threads
        LlmClient.GeneratedTip vtTip = new LlmClient.GeneratedTip();
        vtTip.title = "Virtual Threads (Project Loom) in Java 21";
        vtTip.summary = "Virtual threads are lightweight, JVM-managed threads that decouple Java thread execution from OS platform threads, enabling high-throughput I/O bound concurrency with minimal memory overhead.";
        vtTip.detailedHtml = """
            <h3>1. Core Mechanics & Internal Architecture</h3>
            <p>Traditional OS platform threads carry a heavy memory footprint (~1MB thread stack reserved by the OS kernel) and context switching overhead. Virtual threads (Java 21) are instances of <code>java.lang.Thread</code> managed by the JVM inside user space.</p>
            <p>When a virtual thread executes a blocking I/O operation (e.g. database query, socket read), the JVM unmounts the virtual thread stack from its underlying OS carrier thread (ForkJoinPool worker) and parks it. The carrier thread immediately picks up another runnable virtual thread.</p>

            <h3>2. Production-Grade Java Implementation</h3>
            <p>Modern high-throughput executor pattern using virtual thread per task:</p>
            <pre><code class="language-java">import java.util.concurrent.Executors;
import java.time.Duration;

public class HighThroughputService {
    public void processTaskBatch() {
        // Create an auto-closeable executor that spawns a new virtual thread per task
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50_000; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    // Simulating blocking network / DB call
                    Thread.sleep(Duration.ofMillis(100));
                    return "Task " + taskId + " completed";
                });
            }
        } // AutoCloseable awaitTermination guarantees all 50k virtual threads complete before continuing
    }
}
</code></pre>

            <h3>3. Performance, Memory & JVM Tuning Gotchas</h3>
            <p><strong>Carrier Thread Pinning Pitfall:</strong> If a virtual thread executes a blocking call inside a <code>synchronized</code> block or native method, it gets <em>pinned</em> to its carrier thread, preventing the carrier from serving other tasks. Solution: Replace <code>synchronized</code> blocks with <code>ReentrantLock</code> in I/O-intensive code paths.</p>

            <h3>4. Top Interviewer Trap Questions & Deep Answers</h3>
            <p><strong>Q: Should you pool Virtual Threads using ThreadPoolExecutor?</strong><br>
            <em>Answer:</em> <strong>No, absolute anti-pattern!</strong> Virtual threads are designed to be cheap (~ several hundred bytes initial footprint) and short-lived. Never pool them. Create them dynamically per task using <code>Executors.newVirtualThreadPerTaskExecutor()</code> or <code>Thread.ofVirtual().start()</code>. Use Semaphore if you need to limit access to a downstream resource.</p>
            """;
        fallbackCatalog.put("Virtual Threads (Project Loom, how they differ from Platform Threads)", vtTip);
        fallbackCatalog.put("[Interview Core] Virtual Threads (Project Loom, carrier thread pinning, synchronized block pitfalls, and ReentrantLock replacement)", vtTip);
    }

    /**
     * Obtains a fallback tip for the given concept.
     */
    public LlmClient.GeneratedTip getFallbackTip(String concept) {
        logger.info("Retrieving resilience fallback tip for concept: '{}'", concept);
        
        // Strip category prefix if present for catalog lookup
        String cleanConcept = concept.replaceFirst("^\\[[^\\]]+\\]\\s*", "");
        
        LlmClient.GeneratedTip exactMatch = fallbackCatalog.get(concept);
        if (exactMatch == null) {
            exactMatch = fallbackCatalog.get(cleanConcept);
        }
        if (exactMatch != null) {
            return exactMatch;
        }

        // Generic fallback structured into the 4 sections
        LlmClient.GeneratedTip genericTip = new LlmClient.GeneratedTip();
        genericTip.title = "Java Technical Deep-Dive: " + cleanConcept;
        genericTip.summary = "In-depth architectural breakdown, thread-safety guarantees, and production engineering practices for " + cleanConcept + ".";
        genericTip.detailedHtml = String.format("""
            <h3>1. Core Mechanics & Internal Architecture</h3>
            <p>Understanding the internal mechanisms, thread-safety guarantees, memory layout, and runtime behavior of <em>%s</em> is essential for building resilient high-performance enterprise applications.</p>
            <p>Key JVM considerations include memory visibility, stack vs heap allocation, synchronization barriers, and GC overhead.</p>

            <h3>2. Production-Grade Java Implementation</h3>
            <p>Idiomatic, production-ready code structure demonstrating proper pattern implementation for <em>%s</em>:</p>
            <pre><code class="language-java">// Production Best Practice Implementation for: %s
public final class ProductionMechanismDemo {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductionMechanismDemo.class);

    public void executeOperation() {
        log.info("Executing optimized production pipeline for: {}", "%s");
        // Best practice execution logic
    }
}
</code></pre>

            <h3>3. Performance, Memory & JVM Tuning Gotchas</h3>
            <p>Always analyze GC pressure, cache line contention, lock granularity, and thread pool configuration when applying <em>%s</em> in production high-concurrency environments.</p>

            <h3>4. Top Interviewer Trap Questions & Deep Answers</h3>
            <p><strong>Q: What is the primary architectural pitfall developers encounter with %s?</strong><br>
            <em>Answer:</em> Neglecting edge cases, race conditions, memory leaks, or proxy interception limitations during high-throughput execution. Always follow safe publication practices and explicitly manage resource lifecycles.</p>
            """, cleanConcept, cleanConcept, cleanConcept, cleanConcept, cleanConcept, cleanConcept);
        return genericTip;
    }
}
