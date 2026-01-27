package com.krista.kme.agent.planner.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityResult;
import com.krista.kme.agent.planner.InputVariable;
import com.krista.kme.agent.planner.PlannerAgent;
import com.krista.kme.agent.planner.PlannerResponse;

/**
 * Controller for Planner Agent Web UI
 */
@Controller
public class PlannerController {
    
    private static final Logger logger = LoggerFactory.getLogger(PlannerController.class);
    
    @Autowired
    private PlannerService plannerService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * Serve the main UI page
     */
    @GetMapping("/")
    public String index() {
        return "planner";
    }
    
    /**
     * Handle user request to plan a task
     */
    @MessageMapping("/plan")
    public void plan(Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        String userRequest = (String) request.get("request");

        // Parse input variables if provided
        List<InputVariable> inputVariables = parseInputVariables(request.get("inputVariables"));

        // Parse selected capabilities if provided
        List<Integer> selectedCapabilities = parseSelectedCapabilities(request.get("selectedCapabilities"));

        // Log request details including size
        int totalSize = estimateRequestSize(userRequest, inputVariables);
        logger.info("Received plan request from session {}: {} (total size: {} bytes, {} KB)",
                   sessionId, userRequest, totalSize, totalSize / 1024);

        if (inputVariables != null && !inputVariables.isEmpty()) {
            logger.info("With {} input variables:", inputVariables.size());
            for (InputVariable var : inputVariables) {
                int varSize = var.getValue() != null ? var.getValue().length() : 0;
                logger.info("  - {}: {} bytes ({} KB)",
                           var.getName(), varSize, varSize / 1024);

                if (varSize > 100_000) {
                    logger.warn("⚠️  LARGE INPUT VARIABLE: {} = {} bytes ({} KB). This may cause WebSocket disconnection!",
                               var.getName(), varSize, varSize / 1024);
                }
            }
        }

        if (selectedCapabilities != null && !selectedCapabilities.isEmpty()) {
            logger.info("With {} selected capabilities: {}", selectedCapabilities.size(), selectedCapabilities);
        }

        try {
            PlannerAgent agent = plannerService.getOrCreateAgent(sessionId, selectedCapabilities);

            // Build prompt with input variables if provided
            String prompt = userRequest;
            if (inputVariables != null && !inputVariables.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Input Data:\n");
                for (InputVariable var : inputVariables) {
                    sb.append("- ").append(var.getName()).append(": ").append(var.getValue()).append("\n");
                }
                sb.append("\nUser Request:\n");
                sb.append(userRequest);
                prompt = sb.toString();
            }

            PlannerResponse response = agent.plan(prompt);

            // Check if task is complete and generate usage report
            if (response.isComplete()) {
                generateUsageReportForSession(sessionId);
            }

            // Check for low confidence score on capability selection
            if (response.isCapability() && response.getConfidenceScore() != null && response.getConfidenceScore() < 0.7) {
                // Low confidence - ask user for confirmation
                Map<String, Object> confirmationMap = createConfirmationRequestMap(response);
                logResponseSize(sessionId, "confirmation_request", confirmationMap);
                messagingTemplate.convertAndSend("/topic/response/" + sessionId, confirmationMap);
            } else {
                // Normal flow - send planner response
                Map<String, Object> responseMap = createResponseMap("planner_response", response, null);
                logResponseSize(sessionId, "plan", responseMap);
                messagingTemplate.convertAndSend("/topic/response/" + sessionId, responseMap);
            }

        } catch (Exception e) {
            logger.error("❌ Error planning task for session {}: {}", sessionId, e.getMessage(), e);
            Map<String, Object> errorMap = createErrorMap("Error planning task: " + e.getMessage());
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, errorMap);
        }
    }

    /**
     * Estimate the total size of a request in bytes
     */
    private int estimateRequestSize(String userRequest, List<InputVariable> inputVariables) {
        int size = 0;

        if (userRequest != null) {
            size += userRequest.length();
        }

        if (inputVariables != null) {
            for (InputVariable var : inputVariables) {
                if (var.getName() != null) size += var.getName().length();
                if (var.getValue() != null) size += var.getValue().length();
            }
        }

        return size;
    }

    /**
     * Parse input variables from request
     */
    @SuppressWarnings("unchecked")
    private List<InputVariable> parseInputVariables(Object inputVariablesObj) {
        if (inputVariablesObj == null) {
            return null;
        }

        List<InputVariable> variables = new ArrayList<>();

        try {
            if (inputVariablesObj instanceof List) {
                List<Map<String, String>> varList = (List<Map<String, String>>) inputVariablesObj;
                for (Map<String, String> varMap : varList) {
                    String name = varMap.get("name");
                    String value = varMap.get("value");
                    if (name != null && value != null) {
                        variables.add(new InputVariable(name, value));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse input variables: {}", e.getMessage());
        }

        return variables.isEmpty() ? null : variables;
    }

    /**
     * Parse selected capabilities from request
     */
    @SuppressWarnings("unchecked")
    private List<Integer> parseSelectedCapabilities(Object selectedCapabilitiesObj) {
        if (selectedCapabilitiesObj == null) {
            return null;
        }

        List<Integer> capabilities = new ArrayList<>();

        try {
            if (selectedCapabilitiesObj instanceof List) {
                List<Object> capList = (List<Object>) selectedCapabilitiesObj;
                for (Object cap : capList) {
                    if (cap instanceof Integer) {
                        capabilities.add((Integer) cap);
                    } else if (cap instanceof String) {
                        capabilities.add(Integer.parseInt((String) cap));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse selected capabilities: {}", e.getMessage());
        }

        return capabilities.isEmpty() ? null : capabilities;
    }
    
    /**
     * Handle capability execution
     */
    @MessageMapping("/execute")
    public void execute(Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Integer capabilityId = (Integer) request.get("capabilityId");
        Object inputObj = request.get("input");

        logger.info("Executing capability {} for session {}, input type: {}",
            capabilityId, sessionId, inputObj != null ? inputObj.getClass().getSimpleName() : "null");

        // Convert input to String (handle both String and JSON object)
        String input = convertInputToString(inputObj);

        logger.info("Converted input (length={}): {}",
            input != null ? input.length() : 0,
            input != null && input.length() > 200 ? input.substring(0, 200) + "..." : input);

        try {
            // Execute the capability
            CapabilityResult result = plannerService.executeCapability(capabilityId, input, sessionId);

            // Send execution result to session-specific topic
            Map<String, Object> execResult = createResponseMap("execution_result", result, null);
            logResponseSize(sessionId, "execution_result", execResult);
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, execResult);

            // ALWAYS get next step from planner (success or failure)
            // This allows planner to decide: ask for clarification, report unable, or try different approach
            PlannerAgent agent = plannerService.getOrCreateAgent(sessionId);
            PlannerResponse nextResponse = agent.reportAndPlanNext(
                capabilityId,
                result.toReportString()
            );

            // Check if task is complete and generate usage report
            if (nextResponse.isComplete()) {
                generateUsageReportForSession(sessionId);
            }

            // Send planner response to session-specific topic
            Map<String, Object> responseMap = createResponseMap("planner_response", nextResponse, null);
            logResponseSize(sessionId, "planner_response_after_exec", responseMap);
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, responseMap);

        } catch (Exception e) {
            logger.error("Error executing capability", e);
            Map<String, Object> errorMap = createErrorMap("Error executing capability: " + e.getMessage());
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, errorMap);
        }
    }
    
    /**
     * Handle clarification from user
     */
    @MessageMapping("/clarify")
    public void clarify(Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String clarification = request.get("clarification");

        logger.info("Received clarification from session {}: {}", sessionId, clarification);

        try {
            PlannerAgent agent = plannerService.getOrCreateAgent(sessionId);
            PlannerResponse response = agent.provideClarification(clarification);

            // Check if task is complete and generate usage report
            if (response.isComplete()) {
                generateUsageReportForSession(sessionId);
            }

            Map<String, Object> responseMap = createResponseMap("planner_response", response, null);
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, responseMap);

        } catch (Exception e) {
            logger.error("Error processing clarification", e);
            Map<String, Object> errorMap = createErrorMap("Error processing clarification: " + e.getMessage());
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, errorMap);
        }
    }

    /**
     * Handle user confirmation for low confidence capability selection
     */
    @MessageMapping("/confirm")
    public void confirmCapability(Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Boolean proceed = (Boolean) request.get("proceed");

        // Get the pending response from the request
        @SuppressWarnings("unchecked")
        Map<String, Object> responseData = (Map<String, Object>) request.get("response");

        logger.info("Received confirmation from session {}: proceed={}", sessionId, proceed);

        try {
            if (proceed != null && proceed) {
                // User confirmed - proceed with the capability execution
                PlannerResponse response = reconstructPlannerResponse(responseData);

                Map<String, Object> responseMap = createResponseMap("planner_response", response, null);
                logResponseSize(sessionId, "confirmed_execution", responseMap);
                messagingTemplate.convertAndSend("/topic/response/" + sessionId, responseMap);

            } else {
                // User declined - complete the flow with appropriate message
                PlannerResponse cancelResponse = new PlannerResponse(
                    -2,
                    "Cancelled",
                    "Task cancelled due to low confidence in capability selection. " +
                    "The selected capability had a confidence score below the threshold, " +
                    "and you chose not to proceed. Please try rephrasing your request or " +
                    "provide more specific details."
                );

                // Generate usage report for cancelled session
                generateUsageReportForSession(sessionId);

                Map<String, Object> responseMap = createResponseMap("planner_response", cancelResponse, null);
                logResponseSize(sessionId, "cancelled_low_confidence", responseMap);
                messagingTemplate.convertAndSend("/topic/response/" + sessionId, responseMap);
            }

        } catch (Exception e) {
            logger.error("Error processing confirmation", e);
            Map<String, Object> errorMap = createErrorMap("Error processing confirmation: " + e.getMessage());
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, errorMap);
        }
    }

    /**
     * Reset session
     */
    @MessageMapping("/reset")
    public void reset(Map<String, String> request) {
        String sessionId = request.get("sessionId");
        plannerService.clearSession(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "reset");
        response.put("message", "Session reset successfully");
        messagingTemplate.convertAndSend("/topic/response/" + sessionId, response);
    }

    /**
     * Debug endpoint to check what capabilities an agent has
     */
    @MessageMapping("/debug/capabilities")
    public void debugCapabilities(Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");

        try {
            PlannerAgent agent = plannerService.getSession(sessionId);

            if (agent == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("type", "debug");
                response.put("message", "No agent found for session: " + sessionId);
                messagingTemplate.convertAndSend("/topic/response/" + sessionId, response);
                return;
            }

            Map<Integer, Capability> caps = agent.getCapabilities();
            List<String> capNames = caps.values().stream()
                .map(c -> String.format("ID %d: %s", c.getId(), c.getName()))
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("type", "debug");
            response.put("sessionId", sessionId);
            response.put("capabilityCount", caps.size());
            response.put("capabilities", capNames);
            response.put("message", String.format("Agent has %d capabilities", caps.size()));

            messagingTemplate.convertAndSend("/topic/response/" + sessionId, response);

        } catch (Exception e) {
            logger.error("Error getting debug info: {}", e.getMessage(), e);
            Map<String, Object> errorMap = createErrorMap("Error: " + e.getMessage());
            messagingTemplate.convertAndSend("/topic/response/" + sessionId, errorMap);
        }
    }
    
    private Map<String, Object> createResponseMap(String type, Object data, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", type);
        response.put("data", data);
        if (message != null) {
            response.put("message", message);
        }
        return response;
    }

    private Map<String, Object> createErrorMap(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "error");
        response.put("message", errorMessage);
        return response;
    }

    /**
     * Convert input object to String.
     * Handles both String and JSON object inputs.
     */
    private String convertInputToString(Object inputObj) {
        if (inputObj == null) {
            return null;
        }

        if (inputObj instanceof String) {
            return (String) inputObj;
        }

        // If it's a Map or other object, convert to JSON string
        try {
            // Use Jackson or Gson to convert to JSON string
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(inputObj);
        } catch (Exception e) {
            logger.warn("Failed to convert input to JSON string, using toString(): {}", e.getMessage());
            return inputObj.toString();
        }
    }

    /**
     * Log the size of a response being sent to the client
     */
    private void logResponseSize(String sessionId, String responseType, Map<String, Object> response) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(response);
            int size = json.length();

            logger.info("📤 Sending {} response to session {}: {} bytes ({} KB)",
                       responseType, sessionId, size, size / 1024);

            if (size > 1_000_000) {
                logger.warn("⚠️  LARGE RESPONSE: {} bytes ({} MB). This may cause WebSocket issues!",
                           size, size / (1024 * 1024));
            }
        } catch (Exception e) {
            logger.warn("Failed to estimate response size: {}", e.getMessage());
        }
    }

    /**
     * Generate usage report for a completed session
     */
    private void generateUsageReportForSession(String sessionId) {
        try {
            logger.info("🎯 Task completed for session: {}", sessionId);

            if (plannerService.hasUsageData(sessionId)) {
                String reportPath = plannerService.generateUsageReport(sessionId);
                if (reportPath != null) {
                    logger.info("📊 Usage report generated: {}", reportPath);
                    logger.info("   Session: {}", sessionId);
                } else {
                    logger.debug("No usage report generated (no LLM usage data)");
                }
            } else {
                logger.debug("No usage data for session: {}", sessionId);
            }
        } catch (Exception e) {
            logger.error("Failed to generate usage report for session: {}", sessionId, e);
        }
    }

    /**
     * Create a confirmation request map for low confidence capability selection
     */
    private Map<String, Object> createConfirmationRequestMap(PlannerResponse response) {
        Map<String, Object> confirmationMap = new HashMap<>();
        confirmationMap.put("type", "confirmation_request");
        confirmationMap.put("data", response);
        confirmationMap.put("message", String.format(
            "The planner has selected capability '%s' with a confidence score of %.2f (below the 0.7 threshold). " +
            "This indicates some uncertainty in the selection. Would you like to proceed?",
            response.getName(),
            response.getConfidenceScore()
        ));
        return confirmationMap;
    }

    /**
     * Reconstruct PlannerResponse from Map (received from frontend)
     */
    private PlannerResponse reconstructPlannerResponse(Map<String, Object> data) {
        PlannerResponse response = new PlannerResponse();
        response.setId(((Number) data.get("id")).intValue());
        response.setName((String) data.get("name"));
        response.setDescription((String) data.get("description"));
        response.setInput(data.get("input"));

        if (data.get("confidenceScore") != null) {
            response.setConfidenceScore(((Number) data.get("confidenceScore")).doubleValue());
        }

        return response;
    }
}

