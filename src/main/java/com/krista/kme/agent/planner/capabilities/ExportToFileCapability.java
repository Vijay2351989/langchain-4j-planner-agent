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
            "Export data to file. Input: JSON with {\"data\":\"...\",\"format\":\"CSV|JSON|PDF\",\"filename\":\"...\"}. Output: File path."
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

