package com.krista.kme.agent.planner.capabilities;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example capability that exports data to a file.
 * 
 * Input: JSON with {data, format, filename}
 * Output: File path
 */
public class ExportToFileCapability extends Capability {
    
    private static final Logger logger = LoggerFactory.getLogger(ExportToFileCapability.class);
    
    public ExportToFileCapability() {
        super(
            5,
            "ExportToFile",
            "Exports data to a file in the specified format. Supports CSV, JSON, and PDF formats.",

            "{\n" +
            "  \"data\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The data to export. Include the complete data that needs to be saved to the file.\"\n" +
            "  },\n" +
            "  \"format\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The file format. Options: CSV, JSON, PDF.\"\n" +
            "  },\n" +
            "  \"filename\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The desired filename for the exported file (without extension, it will be added automatically).\"\n" +
            "  }\n" +
            "}"
        );
    }
    
    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing ExportToFile with input length: {}", input != null ? input.length() : 0);
        
        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Export details are required");
        }
        
        try {
            // Simulate file export
            Thread.sleep(300);
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filePath = "/exports/export_" + timestamp + ".csv";
            
            String result = String.format(
                "{\"file_path\":\"%s\",\"size_bytes\":15420,\"format\":\"CSV\"}",
                filePath
            );
            
            return CapabilityResult.success(result, "Data exported successfully to " + filePath);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CapabilityExecutionException("Export interrupted", e);
        }
    }
}

