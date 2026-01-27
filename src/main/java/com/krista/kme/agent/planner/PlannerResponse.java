package com.krista.kme.agent.planner;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured response from the planner agent.
 *
 * Response types:
 * - id > 0: Next capability to execute (includes name, description, input, and confidenceScore)
 * - id = 0: Agent needs clarification (name and description contain clarification details)
 * - id = -1: Agent cannot identify next capability or cannot proceed
 * - id = -2: Task is complete (final answer in description)
 *
 * Confidence Score (for capability selection, id > 0):
 * - Range: 0.0 to 1.0
 * - Indicates LLM's confidence in the selected capability
 * - Low confidence (< 0.7) may trigger user confirmation before execution
 */
public class PlannerResponse {

    @JsonProperty("id")
    private int id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("input")
    private Object input;  // Can be String or JSON object

    @JsonProperty("confidenceScore")
    private Double confidenceScore;  // Confidence score (0.0 to 1.0) for capability selection

    public PlannerResponse() {
    }

    public PlannerResponse(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.input = null;
        this.confidenceScore = null;
    }

    public PlannerResponse(int id, String name, String description, Object input) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.input = input;
        this.confidenceScore = null;
    }

    public PlannerResponse(int id, String name, String description, Object input, Double confidenceScore) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.input = input;
        this.confidenceScore = confidenceScore;
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    public Object getInput() {
        return input;
    }

    public void setInput(Object input) {
        this.input = input;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    /**
     * Get input as String. If input is a JSON object, it will be converted to JSON string.
     */
    public String getInputAsString() {
        if (input == null) {
            return null;
        }
        if (input instanceof String) {
            return (String) input;
        }
        // Convert object to JSON string
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(input);
        } catch (Exception e) {
            // Fallback to toString if JSON conversion fails
            return input.toString();
        }
    }

    /**
     * Check if this response indicates a capability to execute
     */
    public boolean isCapability() {
        return id > 0;
    }
    
    /**
     * Check if this response is a clarification request
     */
    public boolean isClarification() {
        return id == 0;
    }
    
    /**
     * Check if this response indicates the agent cannot proceed
     */
    public boolean isUnableToIdentify() {
        return id == -1;
    }
    
    /**
     * Check if this response indicates task completion
     */
    public boolean isComplete() {
        return id == -2;
    }
    
    @Override
    public String toString() {
        String type;
        if (isCapability()) {
            type = "CAPABILITY";
        } else if (isClarification()) {
            type = "CLARIFICATION";
        } else if (isComplete()) {
            type = "COMPLETE";
        } else {
            type = "UNABLE_TO_IDENTIFY";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("PlannerResponse{type=%s, id=%d, name='%s', description='%s'",
                               type, id, name, description));

        if (input != null) {
            sb.append(String.format(", input='%s'", input));
        }

        if (confidenceScore != null) {
            sb.append(String.format(", confidenceScore=%.2f", confidenceScore));
        }

        sb.append("}");
        return sb.toString();
    }
}

