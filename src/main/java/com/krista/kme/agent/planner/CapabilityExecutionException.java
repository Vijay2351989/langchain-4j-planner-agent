package com.krista.kme.agent.planner;

/**
 * Exception thrown when capability execution fails.
 */
public class CapabilityExecutionException extends Exception {
    
    public CapabilityExecutionException(String message) {
        super(message);
    }
    
    public CapabilityExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

