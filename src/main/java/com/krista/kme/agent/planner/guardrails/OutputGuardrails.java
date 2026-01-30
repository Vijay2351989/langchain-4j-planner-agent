package com.krista.kme.agent.planner.guardrails;

import com.krista.kme.agent.planner.PlannerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Output guardrails to validate LLM responses before execution.
 * 
 * These guardrails ensure:
 * - LLM selected a valid capability
 * - Input parameters are reasonable
 * - No malicious or dangerous operations
 */
public class OutputGuardrails {
    
    private static final Logger logger = LoggerFactory.getLogger(OutputGuardrails.class);
    
    // Configuration
    private static final int MAX_INPUT_SIZE = 50000;  // Max chars in capability input
    
    /**
     * Validate planner response before execution
     */
    public static GuardrailResult validatePlannerResponse(
            PlannerResponse response, 
            Set<Integer> validCapabilityIds) {
        
        if (response == null) {
            return GuardrailResult.blocked("Planner response is null");
        }
        
        // Validate capability ID
        if (response.isCapability()) {
            if (!validCapabilityIds.contains(response.getId())) {
                logger.warn("LLM selected invalid capability ID: {}", response.getId());
                return GuardrailResult.blocked(
                    String.format("Invalid capability ID: %d. Valid IDs: %s", 
                        response.getId(), validCapabilityIds)
                );
            }
        }
        
        // Validate input size
        String inputStr = response.getInputAsString();
        if (inputStr != null && inputStr.length() > MAX_INPUT_SIZE) {
            return GuardrailResult.blocked(
                String.format("Capability input too large (%d chars). Maximum: %d chars",
                    inputStr.length(), MAX_INPUT_SIZE)
            );
        }
        
        // Validate confidence score
        if (response.isCapability()) {
            double confidence = response.getConfidenceScore();
            if (confidence < 0.0 || confidence > 1.0) {
                logger.warn("Invalid confidence score: {}", confidence);
                return GuardrailResult.blocked(
                    String.format("Invalid confidence score: %.2f (must be 0.0-1.0)", confidence)
                );
            }
        }
        
        return GuardrailResult.allowed();
    }
    
    /**
     * Validate capability-specific input based on capability ID
     */
    public static GuardrailResult validateCapabilityInput(int capabilityId, String input) {
        switch (capabilityId) {
            case 4: // SendEmail
                return validateEmailInput(input);
            case 6: // ExecuteScript
                return validateScriptInput(input);
            default:
                return GuardrailResult.allowed();
        }
    }
    
    /**
     * Validate email capability input
     */
    private static GuardrailResult validateEmailInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return GuardrailResult.allowed(); // Let capability handle validation
        }
        
        String lowerInput = input.toLowerCase();
        
        // Block emails to suspicious domains
        String[] blockedDomains = {
            "@example.com",
            "@test.com",
            "@localhost"
        };
        
        for (String domain : blockedDomains) {
            if (lowerInput.contains(domain)) {
                logger.warn("Attempted to send email to blocked domain: {}", domain);
                return GuardrailResult.blocked(
                    "Cannot send emails to test/example domains"
                );
            }
        }
        
        return GuardrailResult.allowed();
    }
    
    /**
     * Validate script execution input
     */
    private static GuardrailResult validateScriptInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return GuardrailResult.allowed();
        }
        
        String lowerInput = input.toLowerCase();
        
        // Block dangerous commands
        String[] dangerousCommands = {
            "rm -rf /",
            "format c:",
            "del /f /s /q",
            "mkfs",
            "dd if=/dev/zero",
            ":(){:|:&};:",  // Fork bomb
            "curl http://",  // Prevent external network calls
            "wget http://",
            "nc -l",  // Netcat listener
            "chmod 777"
        };
        
        for (String cmd : dangerousCommands) {
            if (lowerInput.contains(cmd)) {
                logger.warn("Blocked dangerous script command: {}", cmd);
                return GuardrailResult.blocked(
                    "Script contains potentially dangerous commands"
                );
            }
        }
        
        return GuardrailResult.allowed();
    }
}

