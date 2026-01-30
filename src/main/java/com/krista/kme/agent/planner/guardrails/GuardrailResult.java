package com.krista.kme.agent.planner.guardrails;

/**
 * Result of a guardrail check.
 * Indicates whether an action is allowed or blocked, and why.
 */
public class GuardrailResult {
    private final boolean allowed;
    private final String reason;
    
    private GuardrailResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }
    
    public static GuardrailResult allowed() {
        return new GuardrailResult(true, null);
    }
    
    public static GuardrailResult blocked(String reason) {
        return new GuardrailResult(false, reason);
    }
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public boolean isBlocked() {
        return !allowed;
    }
    
    public String getReason() {
        return reason;
    }
    
    @Override
    public String toString() {
        return allowed ? "ALLOWED" : "BLOCKED: " + reason;
    }
}

