package com.aitips.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages connections and operations on the SQLite database for deduplication and logging of sent tips.
 */
public class DatabaseManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    private final String dbUrl;
    private Connection connection;

    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            logger.debug("Opening new database connection to: {}", dbUrl);
            connection = DriverManager.getConnection(dbUrl);
        }
        return connection;
    }

    private void initializeDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS sent_tips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                concept TEXT UNIQUE NOT NULL,
                sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                title TEXT,
                content TEXT
            );
            """;
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
            logger.info("Database initialized successfully. Schema verified.");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Checks if a concept has already been sent.
     */
    public boolean hasConceptBeenSent(String concept) {
        String sql = "SELECT 1 FROM sent_tips WHERE concept = ? LIMIT 1";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, concept);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking if concept '{}' has been sent", concept, e);
            return false;
        }
    }

    /**
     * Retrieves the set of all concepts that have already been sent.
     */
    public Set<String> getSentConcepts() {
        Set<String> sent = new HashSet<>();
        String sql = "SELECT concept FROM sent_tips";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                sent.add(rs.getString("concept"));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving sent concepts list", e);
        }
        return sent;
    }

    /**
     * Records a sent tip.
     */
    public void recordSentTip(String concept, String title, String content) {
        String sql = "INSERT OR REPLACE INTO sent_tips (concept, title, content) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, concept);
            pstmt.setString(2, title);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
            logger.info("Successfully recorded sent tip for concept: {}", concept);
        } catch (SQLException e) {
            logger.error("Error saving sent tip record for concept: {}", concept, e);
            throw new RuntimeException("Database save failed", e);
        }
    }

    /**
     * Resets the history by clearing all sent tips.
     */
    public void clearHistory() {
        String sql = "DELETE FROM sent_tips";
        try (Statement stmt = getConnection().createStatement()) {
            int rows = stmt.executeUpdate(sql);
            logger.info("Cleared sent tips history. Reset {} records.", rows);
        } catch (SQLException e) {
            logger.error("Failed to clear sent tips database", e);
            throw new RuntimeException("Database reset failed", e);
        }
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("Database connection closed.");
                }
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            } finally {
                connection = null;
            }
        }
    }
}
