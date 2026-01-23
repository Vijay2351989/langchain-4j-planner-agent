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
 *
 * Confidence levels:
 * - HIGH: Method and parameters clearly identified
 * - MEDIUM: Method identified but some parameters may be inferred/guessed
 * - LOW: Method identified but parameters are uncertain or missing
 * - NONE: Cannot identify appropriate method
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

    @JsonProperty("confidence")
    private String confidence;  // HIGH, MEDIUM, LOW, NONE

    @JsonProperty("missingInfo")
    private String missingInfo;  // What information is missing or unclear

    @JsonProperty("needsClarification")
    private Boolean needsClarification;  // Should the planner ask for clarification?
    
    public MethodFinderResponse() {
    }

    public MethodFinderResponse(String methodId, String methodName, String description, String parameters) {
        this.methodId = methodId;
        this.methodName = methodName;
        this.description = description;
        this.parameters = parameters;
        this.confidence = "HIGH";
        this.needsClarification = false;
    }

    public MethodFinderResponse(String methodId, String methodName, String description, String parameters,
                                String confidence, String missingInfo, Boolean needsClarification) {
        this.methodId = methodId;
        this.methodName = methodName;
        this.description = description;
        this.parameters = parameters;
        this.confidence = confidence;
        this.missingInfo = missingInfo;
        this.needsClarification = needsClarification;
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

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getMissingInfo() {
        return missingInfo;
    }

    public void setMissingInfo(String missingInfo) {
        this.missingInfo = missingInfo;
    }

    public Boolean getNeedsClarification() {
        return needsClarification;
    }

    public void setNeedsClarification(Boolean needsClarification) {
        this.needsClarification = needsClarification;
    }

    /**
     * Check if a method was successfully identified
     */
    public boolean hasMethod() {
        return methodId != null && !methodId.isEmpty();
    }

    /**
     * Check if clarification is needed from the user
     */
    public boolean needsClarification() {
        return needsClarification != null && needsClarification;
    }

    /**
     * Check if confidence is high enough to proceed
     */
    public boolean hasHighConfidence() {
        return "HIGH".equalsIgnoreCase(confidence);
    }
    
    @Override
    public String toString() {
        if (hasMethod()) {
            return String.format("MethodFinderResponse{methodId='%s', methodName='%s', confidence='%s', " +
                "needsClarification=%s, parameters='%s', description='%s', missingInfo='%s'}",
                methodId, methodName, confidence, needsClarification, parameters, description, missingInfo);
        } else {
            return String.format("MethodFinderResponse{NO_METHOD_FOUND, confidence='%s', " +
                "needsClarification=%s, description='%s', missingInfo='%s'}",
                confidence, needsClarification, description, missingInfo);
        }
    }
}

