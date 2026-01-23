package com.krista.kme.agent.usage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages LLM usage tracking across all sessions
 */
public class SessionUsageManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionUsageManager.class);
    
    private final Map<String, SessionUsageCollector> sessionCollectors = new ConcurrentHashMap<>();
    private final String reportsDirectory;
    private final UsageReportGenerator reportGenerator;
    
    public SessionUsageManager(String reportsDirectory) {
        this.reportsDirectory = reportsDirectory;
        this.reportGenerator = new UsageReportGenerator();
        
        // Create reports directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(reportsDirectory));
            logger.info("Usage reports will be saved to: {}", reportsDirectory);
        } catch (IOException e) {
            logger.error("Failed to create reports directory: {}", reportsDirectory, e);
        }
    }
    
    /**
     * Get or create a usage collector for a session
     */
    public SessionUsageCollector getCollector(String sessionId) {
        return sessionCollectors.computeIfAbsent(sessionId, SessionUsageCollector::new);
    }
    
    /**
     * Generate and save usage report for a session
     * 
     * @param sessionId The session ID
     * @return Path to the generated report file
     */
    public String generateReport(String sessionId) throws IOException {
        SessionUsageCollector collector = sessionCollectors.get(sessionId);
        
        if (collector == null) {
            logger.warn("No usage data found for session: {}", sessionId);
            return null;
        }
        
        if (collector.getCallCount() == 0) {
            logger.warn("Session {} has no LLM calls recorded", sessionId);
            return null;
        }
        
        // Generate filename
        String filename = String.format("%s.xlsx", sessionId);
        String outputPath = Paths.get(reportsDirectory, filename).toString();
        
        // Generate report
        reportGenerator.generateReport(sessionId, collector, outputPath);
        
        logger.info("✓ Usage report generated for session {}: {}", sessionId, outputPath);
        logger.info("   Total calls: {}, Total cost: ${}", 
                   collector.getCallCount(), 
                   String.format("%.6f", collector.getTotalCost()));
        
        return outputPath;
    }
    
    /**
     * Clear usage data for a session (call this when session is cleared)
     */
    public void clearSession(String sessionId) {
        SessionUsageCollector removed = sessionCollectors.remove(sessionId);
        if (removed != null) {
            logger.debug("Cleared usage data for session: {} ({} calls recorded)", 
                        sessionId, removed.getCallCount());
        }
    }
    
    /**
     * Get usage statistics for a session without generating report
     */
    public SessionUsageCollector getUsageStats(String sessionId) {
        return sessionCollectors.get(sessionId);
    }
    
    /**
     * Check if a session has any recorded usage
     */
    public boolean hasUsageData(String sessionId) {
        SessionUsageCollector collector = sessionCollectors.get(sessionId);
        return collector != null && collector.getCallCount() > 0;
    }
}

