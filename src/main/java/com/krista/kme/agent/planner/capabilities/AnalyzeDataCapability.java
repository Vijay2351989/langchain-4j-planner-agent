package com.krista.kme.agent.planner.capabilities;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example capability that analyzes data.
 * 
 * Input: JSON data from previous capability
 * Output: Analysis summary with statistics
 */
public class AnalyzeDataCapability extends Capability {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalyzeDataCapability.class);
    
    public AnalyzeDataCapability() {
        super(
            2,
            "AnalyzeData",
            "Analyzes data and generates insights, statistics, and trends. Returns a comprehensive analysis summary.",

            "{\n" +
            "  \"data\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The data to analyze, typically a JSON array from a previous capability result. Include the complete data that needs analysis.\"\n" +
            "  }\n" +
            "}"
        );
    }
    
    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing AnalyzeData with input length: {}", input != null ? input.length() : 0);
        
        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Input data is required for analysis");
        }
        
        try {
            // Simulate analysis processing
            Thread.sleep(300);
            
            // Mock analysis based on input
            int recordCount = (int) input.chars().filter(ch -> ch == '{').count();
            
            String analysis = String.format(
                "{\"total_records\":%d," +
                "\"summary\":\"Analyzed %d records\"," +
                "\"insights\":[\"Data shows positive trend\",\"No anomalies detected\"]," +
                "\"statistics\":{\"avg_value\":1563,\"max_value\":2300,\"min_value\":890}}",
                recordCount, recordCount
            );
            
            String message = String.format("Analysis complete: processed %d records, generated insights", recordCount);
            
            return CapabilityResult.success(analysis, message);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CapabilityExecutionException("Analysis interrupted", e);
        }
    }
}

