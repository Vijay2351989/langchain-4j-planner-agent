package com.krista.kme.agent.planner.capabilities;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example capability that sends an email.
 * 
 * Input: JSON with {to, subject, body}
 * Output: Confirmation message
 */
public class SendEmailCapability extends Capability {
    
    private static final Logger logger = LoggerFactory.getLogger(SendEmailCapability.class);
    
    public SendEmailCapability() {
        super(
            4,
            "SendEmail",
            "Send an email. Input: JSON with {\"to\":\"email@example.com\",\"subject\":\"...\",\"body\":\"...\"}. Output: Confirmation message."
        );
    }
    
    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing SendEmail with input: {}", input);
        
        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Email details are required");
        }
        
        try {
            // Simulate email sending
            Thread.sleep(200);
            
            String messageId = "msg_" + System.currentTimeMillis();
            String result = String.format(
                "{\"message_id\":\"%s\",\"status\":\"sent\",\"timestamp\":\"%d\"}",
                messageId, System.currentTimeMillis()
            );
            
            return CapabilityResult.success(result, "Email sent successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CapabilityExecutionException("Email sending interrupted", e);
        }
    }
}

