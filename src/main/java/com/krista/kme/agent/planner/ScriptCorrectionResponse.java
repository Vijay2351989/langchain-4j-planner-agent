package com.krista.kme.agent.planner;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured response from the ScriptCorrectionAgent.
 * 
 * The agent analyzes a failed script and its error message,
 * then returns a corrected version of the script.
 * 
 * Response contains:
 * - scriptType: The type of script (python, bash, etc.)
 * - script: The corrected script code
 * - description: Explanation of what was fixed
 * - fixApplied: Whether a fix was successfully applied
 */
public class ScriptCorrectionResponse {
    
    @JsonProperty("scriptType")
    private String scriptType;
    
    @JsonProperty("script")
    private String script;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("fixApplied")
    private boolean fixApplied;
    
    public ScriptCorrectionResponse() {
    }
    
    public ScriptCorrectionResponse(String scriptType, String script, String description, boolean fixApplied) {
        this.scriptType = scriptType;
        this.script = script;
        this.description = description;
        this.fixApplied = fixApplied;
    }
    
    public String getScriptType() {
        return scriptType;
    }
    
    public void setScriptType(String scriptType) {
        this.scriptType = scriptType;
    }
    
    public String getScript() {
        return script;
    }
    
    public void setScript(String script) {
        this.script = script;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isFixApplied() {
        return fixApplied;
    }
    
    public void setFixApplied(boolean fixApplied) {
        this.fixApplied = fixApplied;
    }
    
    @Override
    public String toString() {
        return String.format("ScriptCorrectionResponse{scriptType='%s', fixApplied=%s, description='%s', scriptLength=%d}",
            scriptType, fixApplied, description, script != null ? script.length() : 0);
    }
}

