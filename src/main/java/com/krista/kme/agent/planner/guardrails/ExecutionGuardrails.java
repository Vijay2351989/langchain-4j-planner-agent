package com.krista.kme.agent.planner.guardrails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Execution guardrails to control what happens during capability execution.
 * 
 * These guardrails enforce:
 * - Rate limiting (prevent abuse)
 * - Resource limits (prevent DoS)
 * - Execution quotas (cost control)
 */
public class ExecutionGuardrails {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionGuardrails.class);
    
    // Configuration
    private static final int MAX_EXECUTIONS_PER_SESSION = 50;  // Max capabilities per session
    private static final int MAX_SCRIPT_EXECUTIONS_PER_SESSION = 10;  // Max scripts per session
    private static final int MAX_EMAIL_SENDS_PER_SESSION = 5;  // Max emails per session
    
    // Session tracking
    private static final Map<String, SessionLimits> sessionLimits = new HashMap<>();
    
    /**
     * Check if capability execution is allowed for this session
     */
    public static GuardrailResult checkExecutionAllowed(String sessionId, int capabilityId) {
        SessionLimits limits = sessionLimits.computeIfAbsent(sessionId, k -> new SessionLimits());
        
        // Check total execution limit
        if (limits.totalExecutions >= MAX_EXECUTIONS_PER_SESSION) {
            logger.warn("Session {} exceeded max executions: {}", sessionId, limits.totalExecutions);
            return GuardrailResult.blocked(
                String.format("Session execution limit reached (%d). Please start a new session.",
                    MAX_EXECUTIONS_PER_SESSION)
            );
        }
        
        // Check capability-specific limits
        switch (capabilityId) {
            case 6: // ExecuteScript
                if (limits.scriptExecutions >= MAX_SCRIPT_EXECUTIONS_PER_SESSION) {
                    return GuardrailResult.blocked(
                        String.format("Script execution limit reached (%d per session)",
                            MAX_SCRIPT_EXECUTIONS_PER_SESSION)
                    );
                }
                break;
                
            case 4: // SendEmail
                if (limits.emailSends >= MAX_EMAIL_SENDS_PER_SESSION) {
                    return GuardrailResult.blocked(
                        String.format("Email send limit reached (%d per session)",
                            MAX_EMAIL_SENDS_PER_SESSION)
                    );
                }
                break;
        }
        
        return GuardrailResult.allowed();
    }
    
    /**
     * Record a capability execution
     */
    public static void recordExecution(String sessionId, int capabilityId) {
        SessionLimits limits = sessionLimits.computeIfAbsent(sessionId, k -> new SessionLimits());
        
        limits.totalExecutions++;
        
        switch (capabilityId) {
            case 6: // ExecuteScript
                limits.scriptExecutions++;
                break;
            case 4: // SendEmail
                limits.emailSends++;
                break;
        }
        
        logger.debug("Session {} execution count: total={}, scripts={}, emails={}",
            sessionId, limits.totalExecutions, limits.scriptExecutions, limits.emailSends);
    }
    
    /**
     * Reset limits for a session (e.g., when session ends)
     */
    public static void resetSession(String sessionId) {
        sessionLimits.remove(sessionId);
        logger.info("Reset execution limits for session: {}", sessionId);
    }
    
    /**
     * Get current execution stats for a session
     */
    public static ExecutionStats getStats(String sessionId) {
        SessionLimits limits = sessionLimits.get(sessionId);
        if (limits == null) {
            return new ExecutionStats(0, 0, 0);
        }
        return new ExecutionStats(
            limits.totalExecutions,
            limits.scriptExecutions,
            limits.emailSends
        );
    }
    
    /**
     * Tracks execution limits per session
     */
    private static class SessionLimits {
        int totalExecutions = 0;
        int scriptExecutions = 0;
        int emailSends = 0;
    }
    
    /**
     * Execution statistics for a session
     */
    public static class ExecutionStats {
        public final int totalExecutions;
        public final int scriptExecutions;
        public final int emailSends;
        
        public ExecutionStats(int totalExecutions, int scriptExecutions, int emailSends) {
            this.totalExecutions = totalExecutions;
            this.scriptExecutions = scriptExecutions;
            this.emailSends = emailSends;
        }
        
        @Override
        public String toString() {
            return String.format("ExecutionStats{total=%d, scripts=%d, emails=%d}",
                totalExecutions, scriptExecutions, emailSends);
        }
    }
}

