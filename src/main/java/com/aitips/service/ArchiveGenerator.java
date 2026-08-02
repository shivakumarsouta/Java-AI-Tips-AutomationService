package com.aitips.service;

import com.aitips.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a static HTML archive website from all previously sent tips stored in SQLite.
 * The output is written to docs/index.html for GitHub Pages deployment.
 */
public class ArchiveGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveGenerator.class);
    private static final String OUTPUT_PATH = "docs/index.html";
    private final DatabaseManager dbManager;

    public ArchiveGenerator(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Queries all sent tips from the database and generates a static docs/index.html website.
     */
    public void generateArchive() {
        logger.info("Generating tip archive website...");
        try {
            List<DatabaseManager.SentTipRecord> tips = dbManager.getAllSentTips();
            if (tips.isEmpty()) {
                logger.warn("No sent tips found in database. Skipping archive generation.");
                return;
            }

            String html = buildArchivePage(tips);
            Path outputPath = Paths.get(OUTPUT_PATH);
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, html, StandardCharsets.UTF_8);
            logger.info("Archive website generated successfully at '{}'. Total tips: {}", OUTPUT_PATH, tips.size());
        } catch (IOException e) {
            logger.error("Failed to write archive HTML file to '{}'", OUTPUT_PATH, e);
        }
    }

    private String buildArchivePage(List<DatabaseManager.SentTipRecord> tips) {
        StringBuilder cards = new StringBuilder();
        // Render in reverse order so the newest tip appears first
        for (int i = tips.size() - 1; i >= 0; i--) {
            DatabaseManager.SentTipRecord tip = tips.get(i);
            String processedHtml = styleCodeBlocksForWeb(tip.content);
            String safeTitle = escapeHtml(tip.title != null ? tip.title : tip.concept);
            String sentDate = tip.sentAt != null ? tip.sentAt.substring(0, 10) : "Unknown Date";

            cards.append("""
                <article class="tip-card" id="tip-%d">
                    <div class="tip-card-header">
                        <div>
                            <span class="tip-number">Tip #%d</span>
                            <h2 class="tip-title">%s</h2>
                        </div>
                        <span class="tip-date">%s</span>
                    </div>
                    <div class="tip-body">
                        %s
                    </div>
                </article>
                """.formatted(i + 1, i + 1, safeTitle, sentDate, processedHtml));
        }

        String lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a"));
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="description" content="A daily archive of AI-generated Java interview tips covering JVM internals, concurrency, design patterns, and modern Java features.">
                <title>Daily Java Interview Tips — Archive</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
                <style>
                    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                    :root {
                        --bg: #0f0f1a;
                        --surface: #1a1a2e;
                        --surface2: #16213e;
                        --border: #2a2a4a;
                        --accent: #818cf8;
                        --accent2: #c084fc;
                        --text: #e2e8f0;
                        --text-muted: #94a3b8;
                        --code-bg: #0d1117;
                        --radius: 12px;
                    }
                    body {
                        background: var(--bg);
                        color: var(--text);
                        font-family: 'Inter', system-ui, sans-serif;
                        font-size: 15px;
                        line-height: 1.7;
                        min-height: 100vh;
                    }

                    /* Hero Header */
                    .hero {
                        background: linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #1e1b4b 100%);
                        padding: 80px 24px 60px;
                        text-align: center;
                        border-bottom: 1px solid var(--border);
                        position: relative;
                        overflow: hidden;
                    }
                    .hero::before {
                        content: '';
                        position: absolute; inset: 0;
                        background: radial-gradient(ellipse at 50% 0%, rgba(129,140,248,0.15) 0%, transparent 70%);
                    }
                    .hero-badge {
                        display: inline-block;
                        background: rgba(129,140,248,0.15);
                        border: 1px solid rgba(129,140,248,0.3);
                        color: var(--accent);
                        font-size: 11px;
                        font-weight: 700;
                        letter-spacing: 0.15em;
                        text-transform: uppercase;
                        padding: 6px 14px;
                        border-radius: 100px;
                        margin-bottom: 20px;
                    }
                    .hero h1 {
                        font-size: clamp(28px, 5vw, 52px);
                        font-weight: 800;
                        color: #fff;
                        letter-spacing: -0.03em;
                        margin-bottom: 16px;
                        position: relative;
                    }
                    .hero h1 span {
                        background: linear-gradient(90deg, var(--accent), var(--accent2));
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        background-clip: text;
                    }
                    .hero p {
                        color: #a5b4fc;
                        font-size: 16px;
                        max-width: 520px;
                        margin: 0 auto 28px;
                    }
                    .stats-bar {
                        display: flex;
                        gap: 32px;
                        justify-content: center;
                        flex-wrap: wrap;
                        position: relative;
                    }
                    .stat {
                        text-align: center;
                    }
                    .stat-value {
                        display: block;
                        font-size: 28px;
                        font-weight: 800;
                        color: #fff;
                        line-height: 1;
                    }
                    .stat-label {
                        font-size: 11px;
                        color: #a5b4fc;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.1em;
                        margin-top: 4px;
                    }

                    /* Main content */
                    .container {
                        max-width: 860px;
                        margin: 0 auto;
                        padding: 48px 24px;
                    }
                    .section-label {
                        font-size: 11px;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.12em;
                        color: var(--accent);
                        margin-bottom: 24px;
                        padding-bottom: 12px;
                        border-bottom: 1px solid var(--border);
                    }

                    /* Tip Cards */
                    .tip-card {
                        background: var(--surface);
                        border: 1px solid var(--border);
                        border-radius: var(--radius);
                        margin-bottom: 24px;
                        overflow: hidden;
                        transition: border-color 0.2s, transform 0.2s;
                    }
                    .tip-card:hover {
                        border-color: rgba(129,140,248,0.4);
                        transform: translateY(-2px);
                    }
                    .tip-card-header {
                        padding: 20px 24px;
                        background: var(--surface2);
                        border-bottom: 1px solid var(--border);
                        display: flex;
                        align-items: flex-start;
                        justify-content: space-between;
                        gap: 16px;
                    }
                    .tip-number {
                        display: inline-block;
                        font-size: 10px;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.1em;
                        color: var(--accent);
                        margin-bottom: 6px;
                    }
                    .tip-title {
                        font-size: 17px;
                        font-weight: 700;
                        color: #fff;
                        line-height: 1.3;
                    }
                    .tip-date {
                        font-size: 11px;
                        color: var(--text-muted);
                        white-space: nowrap;
                        font-weight: 500;
                        margin-top: 2px;
                        flex-shrink: 0;
                    }
                    .tip-body {
                        padding: 24px;
                        color: var(--text-muted);
                    }
                    .tip-body p { margin-bottom: 14px; color: #cbd5e1; }
                    .tip-body h3 { color: var(--accent); font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; margin: 20px 0 10px; }
                    .tip-body ul, .tip-body ol { margin: 0 0 14px 20px; }
                    .tip-body li { margin-bottom: 6px; color: #cbd5e1; }
                    .tip-body strong { color: #e2e8f0; font-weight: 600; }
                    .tip-body code { background: var(--code-bg); color: #a5b4fc; padding: 2px 6px; border-radius: 4px; font-family: 'Fira Code', monospace; font-size: 13px; }
                    .tip-body pre { background: var(--code-bg); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin: 14px 0; overflow-x: auto; }
                    .tip-body pre code { background: none; padding: 0; color: #e2e8f0; font-size: 13px; line-height: 1.6; }

                    /* Footer */
                    footer {
                        text-align: center;
                        padding: 32px 24px;
                        border-top: 1px solid var(--border);
                        color: var(--text-muted);
                        font-size: 12px;
                    }
                    footer strong { color: var(--text); }
                </style>
            </head>
            <body>
                <header class="hero">
                    <div class="hero-badge">Powered by Groq AI + Java</div>
                    <h1>Daily Java <span>Interview Tips</span></h1>
                    <p>An automated archive of AI-generated, production-grade Java interview topics delivered every morning.</p>
                    <div class="stats-bar">
                        <div class="stat">
                            <span class="stat-value">%d</span>
                            <span class="stat-label">Tips Published</span>
                        </div>
                        <div class="stat">
                            <span class="stat-value">Daily</span>
                            <span class="stat-label">Frequency</span>
                        </div>
                        <div class="stat">
                            <span class="stat-value">10 AM</span>
                            <span class="stat-label">IST Delivery</span>
                        </div>
                    </div>
                </header>

                <main class="container">
                    <p class="section-label">All Tips — Newest First</p>
                    %s
                </main>

                <footer>
                    <p><strong>Java AI Tips Automation Service</strong></p>
                    <p style="margin-top:6px">Built with Java 17 · Groq API · GitHub Actions · SQLite · Jakarta Mail</p>
                    <p style="margin-top:4px">Last updated: %s IST</p>
                </footer>
            </body>
            </html>
            """.formatted(tips.size(), cards.toString(), lastUpdated);
    }

    /** Applies basic inline style to pre/code blocks for web rendering */
    private String styleCodeBlocksForWeb(String html) {
        if (html == null) return "";
        return html
                .replaceAll("(?i)<pre[^>]*>\\s*<code[^>]*>", "<pre><code>")
                .replaceAll("(?i)</code>\\s*</pre>", "</code></pre>");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
