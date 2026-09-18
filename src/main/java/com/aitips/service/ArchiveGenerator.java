package com.aitips.service;

import com.aitips.db.DatabaseManager;
import com.aitips.db.DatabaseManager.SentTipRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generates a modern, static HTML5 archive application at docs/index.html for deployment to GitHub Pages.
 */
public class ArchiveGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveGenerator.class);

    private final DatabaseManager dbManager;

    public ArchiveGenerator(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Rebuilds docs/index.html with all sent tips retrieved from the SQLite database.
     */
    public void generateArchive() {
        logger.info("Generating GitHub Pages newsletter archive website at docs/index.html...");
        try {
            List<SentTipRecord> records = dbManager.getAllSentTipRecords();
            String htmlContent = buildArchiveHtml(records);

            Path docsDir = Paths.get("docs");
            if (!Files.exists(docsDir)) {
                Files.createDirectories(docsDir);
            }

            Path indexPath = docsDir.resolve("index.html");
            Files.writeString(indexPath, htmlContent, StandardCharsets.UTF_8);
            logger.info("Successfully generated GitHub Pages archive website with {} tips.", records.size());

        } catch (IOException e) {
            logger.error("Failed to write docs/index.html archive file", e);
        }
    }

    private String buildArchiveHtml(List<SentTipRecord> records) {
        StringBuilder cardsHtml = new StringBuilder();

        if (records.isEmpty()) {
            cardsHtml.append("""
                <div class="empty-state">
                    <div class="empty-icon">💡</div>
                    <h3>No Tips Archived Yet</h3>
                    <p>The daily Java technical tip service is initialized. Scheduled tasks run daily at 10:00 AM IST!</p>
                </div>
                """);
        } else {
            for (int i = 0; i < records.size(); i++) {
                SentTipRecord rec = records.get(i);
                String issueBadge = (i == 0) ? "<span class=\"badge badge-latest\">★ Latest Issue</span>" : "<span class=\"badge\">Issue #" + rec.id() + "</span>";
                
                // Extract category tag if present
                String conceptRaw = rec.concept();
                String categoryPill = "";
                String categoryClass = "all";
                String cleanConcept = conceptRaw;

                if (conceptRaw.startsWith("[Common Misconceptions]")) {
                    categoryPill = "<span class=\"pill pill-misconception\">Common Misconception</span>";
                    categoryClass = "misconception";
                    cleanConcept = conceptRaw.replace("[Common Misconceptions]", "").trim();
                } else if (conceptRaw.startsWith("[Interview Core]")) {
                    categoryPill = "<span class=\"pill pill-interview\">Interview Core</span>";
                    categoryClass = "interview";
                    cleanConcept = conceptRaw.replace("[Interview Core]", "").trim();
                } else if (conceptRaw.startsWith("[Full Stack Essentials]")) {
                    categoryPill = "<span class=\"pill pill-fullstack\">Full Stack Essential</span>";
                    categoryClass = "fullstack";
                    cleanConcept = conceptRaw.replace("[Full Stack Essentials]", "").trim();
                }

                cardsHtml.append(String.format("""
                    <div class="tip-card" data-title="%s" data-concept="%s" data-category="%s">
                        <div class="card-header" onclick="toggleCard(this)">
                            <div>
                                <div class="badge-row">
                                    %s
                                    %s
                                </div>
                                <h2 class="card-title">%s</h2>
                                <span class="card-meta">Published on %s • Topic: %s</span>
                            </div>
                            <span class="chevron">▼</span>
                        </div>
                        <div class="card-body">
                            %s
                        </div>
                    </div>
                    """,
                    escapeAttr(rec.title()),
                    escapeAttr(conceptRaw),
                    categoryClass,
                    issueBadge,
                    categoryPill,
                    escapeHtml(rec.title()),
                    escapeHtml(rec.sentAt()),
                    escapeHtml(cleanConcept),
                    rec.content()
                ));
            }
        }

        String template = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Java AI Daily Tips - Technical Architecture & Interview Archive</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
                <style>
                    :root {
                        --bg-main: #0b0f19;
                        --bg-card: #111827;
                        --bg-card-hover: #1e293b;
                        --border-color: #1e293b;
                        --accent-color: #38bdf8;
                        --text-main: #e2e8f0;
                        --text-muted: #94a3b8;
                        --text-bright: #ffffff;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background-color: var(--bg-main);
                        color: var(--text-main);
                        font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        line-height: 1.65;
                        padding-bottom: 80px;
                        -webkit-font-smoothing: antialiased;
                    }
                    .header {
                        background: radial-gradient(circle at 50% 0%, #1e293b 0%, #0f172a 70%, #0b0f19 100%);
                        padding: 70px 20px 60px 20px;
                        text-align: center;
                        border-bottom: 1px solid var(--border-color);
                        position: relative;
                    }
                    .header::after {
                        content: '';
                        position: absolute;
                        bottom: -1px;
                        left: 50%;
                        transform: translateX(-50%);
                        width: 200px;
                        height: 2px;
                        background: linear-gradient(90deg, transparent, var(--accent-color), transparent);
                    }
                    .header-tag {
                        display: inline-block;
                        padding: 5px 14px;
                        background: rgba(56, 189, 248, 0.1);
                        color: var(--accent-color);
                        border: 1px solid rgba(56, 189, 248, 0.25);
                        border-radius: 9999px;
                        font-size: 0.75rem;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.1em;
                        margin-bottom: 16px;
                    }
                    .header h1 {
                        font-size: 2.5rem;
                        font-weight: 800;
                        color: var(--text-bright);
                        letter-spacing: -0.025em;
                        margin-bottom: 14px;
                        line-height: 1.2;
                    }
                    .header p {
                        font-size: 1.05rem;
                        color: var(--text-muted);
                        max-width: 680px;
                        margin: 0 auto 26px auto;
                    }
                    .header-actions {
                        display: flex;
                        justify-content: center;
                        gap: 12px;
                        flex-wrap: wrap;
                    }
                    .btn-github {
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        background: rgba(255, 255, 255, 0.06);
                        color: var(--text-bright);
                        padding: 10px 22px;
                        border-radius: 9999px;
                        text-decoration: none;
                        font-weight: 600;
                        font-size: 0.875rem;
                        border: 1px solid #334155;
                        transition: all 0.2s ease;
                    }
                    .btn-github:hover {
                        background: rgba(255, 255, 255, 0.12);
                        border-color: var(--text-muted);
                        transform: translateY(-2px);
                    }
                    .container {
                        max-width: 900px;
                        margin: 40px auto 0 auto;
                        padding: 0 20px;
                    }
                    .controls {
                        margin-bottom: 28px;
                    }
                    .search-input {
                        width: 100%;
                        padding: 15px 22px;
                        background-color: var(--bg-card);
                        border: 1px solid var(--border-color);
                        border-radius: 12px;
                        color: var(--text-bright);
                        font-size: 0.95rem;
                        outline: none;
                        transition: border-color 0.2s ease, box-shadow 0.2s ease;
                    }
                    .search-input:focus {
                        border-color: var(--accent-color);
                        box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15);
                    }
                    .filter-pills {
                        display: flex;
                        gap: 8px;
                        margin-top: 14px;
                        flex-wrap: wrap;
                    }
                    .filter-btn {
                        padding: 6px 14px;
                        background-color: var(--bg-card);
                        border: 1px solid var(--border-color);
                        border-radius: 9999px;
                        color: var(--text-muted);
                        font-size: 0.8rem;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        user-select: none;
                    }
                    .filter-btn:hover {
                        border-color: #334155;
                        color: var(--text-bright);
                    }
                    .filter-btn.active {
                        background-color: #0284c7;
                        border-color: #38bdf8;
                        color: #ffffff;
                    }
                    .badge-row {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                        margin-bottom: 6px;
                    }
                    .badge {
                        display: inline-block;
                        padding: 3px 10px;
                        background-color: #1e293b;
                        color: #94a3b8;
                        border-radius: 9999px;
                        font-size: 0.7rem;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                    }
                    .badge-latest {
                        background-color: #0369a1;
                        color: #e0f2fe;
                        border: 1px solid #0284c7;
                    }
                    .pill {
                        display: inline-block;
                        padding: 3px 10px;
                        border-radius: 9999px;
                        font-size: 0.7rem;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.04em;
                    }
                    .pill-misconception {
                        background: rgba(217, 119, 6, 0.15);
                        color: #fbbf24;
                        border: 1px solid rgba(245, 158, 11, 0.3);
                    }
                    .pill-interview {
                        background: rgba(14, 165, 233, 0.15);
                        color: #38bdf8;
                        border: 1px solid rgba(56, 189, 248, 0.3);
                    }
                    .pill-fullstack {
                        background: rgba(16, 185, 129, 0.15);
                        color: #34d399;
                        border: 1px solid rgba(52, 211, 153, 0.3);
                    }
                    .tip-card {
                        background-color: var(--bg-card);
                        border: 1px solid var(--border-color);
                        border-radius: 14px;
                        margin-bottom: 18px;
                        overflow: hidden;
                        transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
                    }
                    .tip-card:hover {
                        border-color: #334155;
                        box-shadow: 0 10px 30px -8px rgba(0, 0, 0, 0.45);
                    }
                    .card-header {
                        padding: 22px 26px;
                        cursor: pointer;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        user-select: none;
                    }
                    .card-title {
                        font-size: 1.25rem;
                        font-weight: 700;
                        color: var(--text-bright);
                        margin: 6px 0 4px 0;
                        line-height: 1.35;
                    }
                    .card-meta {
                        font-size: 0.85rem;
                        color: var(--text-muted);
                    }
                    .chevron {
                        color: var(--text-muted);
                        font-size: 0.85rem;
                        transition: transform 0.3s ease;
                        margin-left: 16px;
                    }
                    .tip-card.active .chevron {
                        transform: rotate(180deg);
                    }
                    .card-body {
                        display: none;
                        padding: 0 26px 26px 26px;
                        border-top: 1px solid var(--border-color);
                        margin-top: 4px;
                        padding-top: 22px;
                        color: #cbd5e1;
                    }
                    .card-body h3 {
                        color: var(--text-bright);
                        font-size: 1.05rem;
                        font-weight: 700;
                        margin: 26px 0 12px 0;
                        padding-bottom: 8px;
                        border-bottom: 1px solid #1e293b;
                        letter-spacing: -0.01em;
                    }
                    .tip-card.active .card-body {
                        display: block;
                    }
                    pre {
                        background-color: #030712 !important;
                        border: 1px solid #1e293b !important;
                        border-radius: 8px;
                        padding: 18px 20px;
                        overflow-x: auto;
                        font-family: 'JetBrains Mono', Consolas, monospace;
                        font-size: 0.875rem;
                        margin: 18px 0;
                        position: relative;
                        line-height: 1.55;
                    }
                    ul, ol { padding-left: 22px; margin-bottom: 16px; }
                    li { margin-bottom: 8px; }
                    p { margin-bottom: 14px; }
                    strong { color: var(--text-bright); }
                    code {
                        background-color: #1e293b;
                        color: #e2e8f0;
                        padding: 2px 7px;
                        border-radius: 4px;
                        font-family: 'JetBrains Mono', Consolas, monospace;
                        font-size: 88%;
                    }
                    .empty-state {
                        text-align: center;
                        padding: 60px 20px;
                        background-color: var(--bg-card);
                        border-radius: 14px;
                        border: 1px solid var(--border-color);
                    }
                    .empty-icon { font-size: 2.5rem; margin-bottom: 12px; }
                    .footer {
                        text-align: center;
                        margin-top: 60px;
                        color: var(--text-muted);
                        font-size: 0.85rem;
                        border-top: 1px solid var(--border-color);
                        padding-top: 30px;
                    }
                    .copy-btn {
                        position: absolute;
                        top: 10px;
                        right: 10px;
                        background: #1e293b;
                        color: #94a3b8;
                        border: 1px solid #334155;
                        padding: 4px 10px;
                        border-radius: 6px;
                        font-size: 0.75rem;
                        font-weight: 600;
                        cursor: pointer;
                        opacity: 0;
                        transition: opacity 0.2s ease, background-color 0.2s ease;
                    }
                    pre:hover .copy-btn { opacity: 1; }
                    .copy-btn:hover { background: #334155; color: #ffffff; }
                </style>
            </head>
            <body>
                <header class="header">
                    <span class="header-tag">JAVA ENGINEERING &amp; ARCHITECTURE</span>
                    <h1>Java AI Daily Interview Tips</h1>
                    <p>Automated daily publication dissecting deep JVM internals, full-stack Java architecture, and top interview trap questions.</p>
                    <div class="header-actions">
                        <a href="https://github.com/shivakumarsouta/Java-AI-Tips-AutomationService" target="_blank" class="btn-github">
                            <svg height="18" width="18" viewBox="0 0 16 16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.28.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg>
                            View Repository on GitHub
                        </a>
                    </div>
                </header>

                <main class="container">
                    <div class="controls">
                        <input type="text" id="searchInput" class="search-input" placeholder="🔍 Search Java topics, JVM flags, Spring, threads, GC..." onkeyup="filterTips()">
                        <div class="filter-pills">
                            <button class="filter-btn active" onclick="setCategoryFilter('all', this)">All Categories</button>
                            <button class="filter-btn" onclick="setCategoryFilter('misconception', this)">Common Misconceptions</button>
                            <button class="filter-btn" onclick="setCategoryFilter('interview', this)">Interview Core</button>
                            <button class="filter-btn" onclick="setCategoryFilter('fullstack', this)">Full Stack Essentials</button>
                        </div>
                    </div>

                    <div id="tipsList">
                        {content}
                    </div>
                </main>

                <footer class="footer">
                    <p>Powered by Java 17 • Groq LLM API • SQLite • GitHub Actions</p>
                </footer>

                <script>
                    let activeCategory = 'all';

                    function toggleCard(headerElem) {
                        const card = headerElem.parentElement;
                        card.classList.toggle('active');
                    }

                    function setCategoryFilter(cat, btnElem) {
                        activeCategory = cat;
                        document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
                        btnElem.classList.add('active');
                        filterTips();
                    }

                    function filterTips() {
                        const query = document.getElementById('searchInput').value.toLowerCase();
                        const cards = document.querySelectorAll('.tip-card');
                        cards.forEach(card => {
                            const title = card.getAttribute('data-title').toLowerCase();
                            const concept = card.getAttribute('data-concept').toLowerCase();
                            const category = card.getAttribute('data-category');

                            const matchesQuery = title.includes(query) || concept.includes(query);
                            const matchesCat = (activeCategory === 'all' || category === activeCategory);

                            if (matchesQuery && matchesCat) {
                                card.style.display = 'block';
                            } else {
                                card.style.display = 'none';
                            }
                        });
                    }

                    // Attach Copy Code buttons to pre blocks dynamically
                    document.addEventListener('DOMContentLoaded', () => {
                        const firstCard = document.querySelector('.tip-card');
                        if (firstCard) firstCard.classList.add('active');

                        document.querySelectorAll('pre').forEach(pre => {
                            const btn = document.createElement('button');
                            btn.className = 'copy-btn';
                            btn.innerText = 'Copy';
                            btn.onclick = (e) => {
                                e.stopPropagation();
                                const code = pre.querySelector('code') ? pre.querySelector('code').innerText : pre.innerText;
                                navigator.clipboard.writeText(code).then(() => {
                                    btn.innerText = 'Copied!';
                                    setTimeout(() => btn.innerText = 'Copy', 2000);
                                });
                            };
                            pre.appendChild(btn);
                        });
                    });
                </script>
            </body>
            </html>
            """;
        return template.replace("{content}", cardsHtml.toString());
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    private String escapeAttr(String input) {
        if (input == null) return "";
        return escapeHtml(input).replace("\"", "&quot;");
    }
}
