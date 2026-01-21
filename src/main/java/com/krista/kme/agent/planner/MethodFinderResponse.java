package com.krista.kme.agent.planner;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured response from the MethodFinderAgent.
 * 
 * The agent analyzes the task description and available methods,
 * then returns which method to use and what parameters to pass.
 * 
 * Response types:
 * - methodId != null: Method identified (includes parameters)
 * - methodId == null: Cannot identify appropriate method (reason in description)
 */
public class MethodFinderResponse {
    
    @JsonProperty("methodId")
    private String methodId;
    
    @JsonProperty("methodName")
    private String methodName;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("parameters")
    private String parameters;
    
    public MethodFinderResponse() {
    }
    
    public MethodFinderResponse(String methodId, String methodName, String description, String parameters) {
        this.methodId = methodId;
        this.methodName = methodName;
        this.description = description;
        this.parameters = parameters;
    }
    
    public String getMethodId() {
        return methodId;
    }
    
    public void setMethodId(String methodId) {
        this.methodId = methodId;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getParameters() {
        return parameters;
    }
    
    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
    
    /**
     * Check if a method was successfully identified
     */
    public boolean hasMethod() {
        return methodId != null && !methodId.isEmpty();
    }
    
    @Override
    public String toString() {
        if (hasMethod()) {
            return String.format("MethodFinderResponse{methodId='%s', methodName='%s', description='%s', parameters='%s'}",
                methodId, methodName, description, parameters);
        } else {
            return String.format("MethodFinderResponse{NO_METHOD_FOUND, description='%s'}", description);
        }
    }
}

