package com.krista.kme.agent.planner.capabilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capability that sends an email.
 *
 * Input: JSON with {to, subject, body}
 * Output: Confirmation message with email details
 *
 * This is a MOCK implementation that logs what would be sent but doesn't actually send emails.
 * In production, this would integrate with an actual email service (SMTP, SendGrid, etc.)
 */
public class SendEmailCapability extends Capability {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailCapability.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public SendEmailCapability() {
        super(
            4,
            "SendEmail",
            "Sends an email to a specified recipient with a subject and body content. " +
            "Use this capability when the user explicitly requests to send an email or deliver information via email. " +
            "The email will be sent immediately upon execution.",

            // Input schema - structured field definitions
            "{\n" +
            "  \"to\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The recipient's email address. MUST be extracted from the user's request.\"\n" +
            "  },\n" +
            "  \"subject\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The email subject line. Should be descriptive and relevant to the content being sent.\"\n" +
            "  },\n" +
            "  \"body\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The email body content. MUST contain the actual data/content from previous capability results. Include the complete data that needs to be sent.\"\n" +
            "  }\n" +
            "}"
        );
    }

    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing SendEmail capability");
        logger.debug("Raw input: {}", input);

        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Email details are required");
        }

        try {
            // Parse the JSON input
            EmailInfo emailInfo = parseEmailInput(input);

            // Log what we're about to "send"
            logger.info("📧 EMAIL DETAILS:");
            logger.info("   To: {}", emailInfo.to);
            logger.info("   Subject: {}", emailInfo.subject);
            logger.info("   Body length: {} characters", emailInfo.body.length());
            logger.info("   Body preview: {}",
                       emailInfo.body.length() > 200
                           ? emailInfo.body.substring(0, 200) + "..."
                           : emailInfo.body);

            // Simulate email sending
            Thread.sleep(200);

            String messageId = "msg_" + System.currentTimeMillis();

            // Return detailed result
            String result = String.format(
                "{\"message_id\":\"%s\"," +
                "\"status\":\"sent\"," +
                "\"to\":\"%s\"," +
                "\"subject\":\"%s\"," +
                "\"body_length\":%d," +
                "\"timestamp\":%d}",
                messageId,
                escapeJson(emailInfo.to),
                escapeJson(emailInfo.subject),
                emailInfo.body.length(),
                System.currentTimeMillis()
            );

            String message = String.format(
                "✅ Email sent successfully to %s with subject '%s' (%d characters in body)",
                emailInfo.to, emailInfo.subject, emailInfo.body.length()
            );

            return CapabilityResult.success(result, message);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CapabilityExecutionException("Email sending interrupted", e);
        } catch (CapabilityExecutionException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to parse email input", e);
            throw new CapabilityExecutionException("Failed to parse email input: " + e.getMessage(), e);
        }
    }

    /**
     * Parse email input from JSON
     */
    private EmailInfo parseEmailInput(String input) throws CapabilityExecutionException {
        String trimmedInput = input.trim();

        // Try to parse as JSON
        if (trimmedInput.startsWith("{")) {
            try {
                JsonNode rootNode = objectMapper.readTree(trimmedInput);

                String to = rootNode.has("to") ? rootNode.get("to").asText() : null;
                String subject = rootNode.has("subject") ? rootNode.get("subject").asText("No Subject") : "No Subject";
                String body = rootNode.has("body") ? rootNode.get("body").asText() : null;

                if (to == null || to.trim().isEmpty()) {
                    throw new CapabilityExecutionException(
                        "Recipient email address ('to' field) is required. " +
                        "Please extract the email address from the user's request."
                    );
                }

                if (body == null || body.trim().isEmpty()) {
                    throw new CapabilityExecutionException(
                        "Email body is required. " +
                        "Please include the actual content/data in the 'body' field, not placeholder text."
                    );
                }

                // Validate email format (basic check)
                if (!to.contains("@") || !to.contains(".")) {
                    logger.warn("Email address '{}' may not be valid", to);
                }

                return new EmailInfo(to, subject, body);

            } catch (CapabilityExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new CapabilityExecutionException(
                    "Failed to parse JSON input. Expected format: " +
                    "{\"to\":\"email@example.com\",\"subject\":\"...\",\"body\":\"...\"}. " +
                    "Error: " + e.getMessage(), e
                );
            }
        } else {
            throw new CapabilityExecutionException(
                "Input must be JSON format with 'to', 'subject', and 'body' fields. " +
                "Example: {\"to\":\"user@example.com\",\"subject\":\"Hello\",\"body\":\"Message content\"}"
            );
        }
    }

    /**
     * Escape special characters for JSON
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Email information holder
     */
    private static class EmailInfo {
        final String to;
        final String subject;
        final String body;

        EmailInfo(String to, String subject, String body) {
            this.to = to;
            this.subject = subject;
            this.body = body;
        }
    }
}

