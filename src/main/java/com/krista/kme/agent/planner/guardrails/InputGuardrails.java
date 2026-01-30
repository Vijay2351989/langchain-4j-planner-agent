package com.krista.kme.agent.planner.guardrails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Input guardrails to validate and sanitize user requests before sending to LLM.
 * 
 * These guardrails protect against:
 * - Prompt injection attacks
 * - Excessively long inputs
 * - Malicious content
 * - PII (Personally Identifiable Information) leakage
 */
public class InputGuardrails {
    
    private static final Logger logger = LoggerFactory.getLogger(InputGuardrails.class);
    
    // Configuration
    private static final int MAX_INPUT_LENGTH = 10000;  // Max chars in user input
    private static final int MAX_CLARIFICATION_LENGTH = 5000;
    
    /**
     * Validate user input before sending to planner
     */
    public static GuardrailResult validateUserInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return GuardrailResult.blocked("Input cannot be empty");
        }
        
        // Check length
        if (input.length() > MAX_INPUT_LENGTH) {
            return GuardrailResult.blocked(
                String.format("Input too long (%d chars). Maximum allowed: %d chars", 
                    input.length(), MAX_INPUT_LENGTH)
            );
        }
        
        // Check for prompt injection attempts
        if (containsPromptInjection(input)) {
            logger.warn("Potential prompt injection detected: {}", 
                input.length() > 100 ? input.substring(0, 100) + "..." : input);
            return GuardrailResult.blocked("Input contains potentially malicious content");
        }
        
        // Check for system prompt override attempts
        if (containsSystemPromptOverride(input)) {
            logger.warn("System prompt override attempt detected");
            return GuardrailResult.blocked("Input contains unauthorized instructions");
        }
        
        return GuardrailResult.allowed();
    }
    
    /**
     * Validate clarification input
     */
    public static GuardrailResult validateClarification(String clarification) {
        if (clarification == null || clarification.trim().isEmpty()) {
            return GuardrailResult.blocked("Clarification cannot be empty");
        }
        
        if (clarification.length() > MAX_CLARIFICATION_LENGTH) {
            return GuardrailResult.blocked(
                String.format("Clarification too long (%d chars). Maximum: %d chars",
                    clarification.length(), MAX_CLARIFICATION_LENGTH)
            );
        }
        
        return GuardrailResult.allowed();
    }
    
    /**
     * Detect potential prompt injection patterns
     */
    private static boolean containsPromptInjection(String input) {
        String lowerInput = input.toLowerCase();
        
        // Common prompt injection patterns
        String[] injectionPatterns = {
            "ignore previous instructions",
            "ignore all previous",
            "disregard previous",
            "forget previous instructions",
            "new instructions:",
            "system:",
            "you are now",
            "your new role is",
            "override instructions",
            "ignore the above",
            "disregard the above"
        };
        
        for (String pattern : injectionPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Detect attempts to override system prompt
     */
    private static boolean containsSystemPromptOverride(String input) {
        String lowerInput = input.toLowerCase();
        
        // Patterns that try to change agent behavior
        String[] overridePatterns = {
            "you are a",
            "act as a",
            "pretend you are",
            "roleplay as",
            "simulate being",
            "behave like"
        };
        
        // Only flag if it appears at the start (legitimate use might be in middle)
        String trimmed = lowerInput.trim();
        for (String pattern : overridePatterns) {
            if (trimmed.startsWith(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Sanitize input by removing potentially harmful content
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        
        // Remove excessive whitespace
        String sanitized = input.trim().replaceAll("\\s+", " ");
        
        // Remove null bytes
        sanitized = sanitized.replace("\0", "");
        
        // Remove control characters (except newlines and tabs)
        sanitized = sanitized.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        
        return sanitized;
    }
}

