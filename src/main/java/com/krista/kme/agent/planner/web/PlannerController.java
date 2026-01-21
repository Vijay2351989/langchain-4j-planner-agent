package com.krista.kme.agent.planner.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
    @SendTo("/topic/response")
    public Map<String, Object> plan(Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        String userRequest = (String) request.get("request");

        // Parse input variables if provided
        List<InputVariable> inputVariables = parseInputVariables(request.get("inputVariables"));

        // Parse selected capabilities if provided
        List<Integer> selectedCapabilities = parseSelectedCapabilities(request.get("selectedCapabilities"));

        logger.info("Received plan request from session {}: {}", sessionId, userRequest);
        if (inputVariables != null && !inputVariables.isEmpty()) {
            logger.info("With {} input variables", inputVariables.size());
        }
        if (selectedCapabilities != null && !selectedCapabilities.isEmpty()) {
            logger.info("With {} selected capabilities: {}", selectedCapabilities.size(), selectedCapabilities);
        }

        try {
            PlannerAgent agent = plannerService.getOrCreateAgent(sessionId, selectedCapabilities);
            PlannerResponse response = agent.plan(userRequest, inputVariables);

            return createResponseMap("planner_response", response, null);

        } catch (Exception e) {
            logger.error("Error planning task", e);
            return createErrorMap("Error planning task: " + e.getMessage());
        }
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
    @SendTo("/topic/response")
    public Map<String, Object> execute(Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Integer capabilityId = (Integer) request.get("capabilityId");
        String input = (String) request.get("input");
        
        logger.info("Executing capability {} for session {}", capabilityId, sessionId);
        
        try {
            // Execute the capability
            CapabilityResult result = plannerService.executeCapability(capabilityId, input);
            
            // Send execution result
            Map<String, Object> execResult = createResponseMap("execution_result", result, null);
            messagingTemplate.convertAndSend("/topic/response", execResult);
            
            // If successful, get next step from planner
            if (result.isSuccess()) {
                PlannerAgent agent = plannerService.getOrCreateAgent(sessionId);
                PlannerResponse nextResponse = agent.reportAndPlanNext(
                    capabilityId, 
                    result.toReportString(),
                    300
                );
                
                return createResponseMap("planner_response", nextResponse, null);
            } else {
                return createErrorMap("Capability execution failed: " + result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error executing capability", e);
            return createErrorMap("Error executing capability: " + e.getMessage());
        }
    }
    
    /**
     * Handle clarification from user
     */
    @MessageMapping("/clarify")
    @SendTo("/topic/response")
    public Map<String, Object> clarify(Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String clarification = request.get("clarification");
        
        logger.info("Received clarification from session {}: {}", sessionId, clarification);
        
        try {
            PlannerAgent agent = plannerService.getOrCreateAgent(sessionId);
            PlannerResponse response = agent.provideClarification(clarification);
            
            return createResponseMap("planner_response", response, null);
            
        } catch (Exception e) {
            logger.error("Error processing clarification", e);
            return createErrorMap("Error processing clarification: " + e.getMessage());
        }
    }
    
    /**
     * Reset session
     */
    @MessageMapping("/reset")
    @SendTo("/topic/response")
    public Map<String, Object> reset(Map<String, String> request) {
        String sessionId = request.get("sessionId");
        plannerService.clearSession(sessionId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "reset");
        response.put("message", "Session reset successfully");
        return response;
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
}

