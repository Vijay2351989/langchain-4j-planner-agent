package com.krista.kme.agent.planner;

/**
 * Result of executing a capability.
 * 
 * Contains:
 * - success: Whether execution succeeded
 * - output: Output data that can be passed to next capability or planner
 * - message: Human-readable message about what happened
 */
public class CapabilityResult {
    
    private final boolean success;
    private final String output;
    private final String message;
    
    private CapabilityResult(boolean success, String output, String message) {
        this.success = success;
        this.output = output;
        this.message = message;
    }
    
    /**
     * Create a successful result
     * 
     * @param output Output data (can be passed to next capability)
     * @param message Human-readable success message
     * @return Success result
     */
    public static CapabilityResult success(String output, String message) {
        return new CapabilityResult(true, output, message);
    }
    
    /**
     * Create a successful result with just output
     * 
     * @param output Output data
     * @return Success result
     */
    public static CapabilityResult success(String output) {
        return new CapabilityResult(true, output, "Success");
    }
    
    /**
     * Create a failure result
     * 
     * @param message Error message
     * @return Failure result
     */
    public static CapabilityResult failure(String message) {
        return new CapabilityResult(false, null, message);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getOutput() {
        return output;
    }
    
    public String getMessage() {
        return message;
    }
    
    /**
     * Get a formatted string suitable for reporting to the planner
     */
    public String toReportString() {
        if (success) {
            return String.format("SUCCESS: %s | Output: %s", message, output);
        } else {
            return String.format("FAILURE: %s", message);
        }
    }
    
    @Override
    public String toString() {
        return String.format("CapabilityResult{success=%s, message='%s', output='%s'}", 
            success, message, output);
    }
}

