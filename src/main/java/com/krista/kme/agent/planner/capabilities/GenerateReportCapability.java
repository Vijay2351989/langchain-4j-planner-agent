package com.krista.kme.agent.planner.capabilities;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example capability that generates a report.
 * 
 * Input: Analysis data from previous capability
 * Output: Report file path or content
 */
public class GenerateReportCapability extends Capability {
    
    private static final Logger logger = LoggerFactory.getLogger(GenerateReportCapability.class);
    
    public GenerateReportCapability() {
        super(
            3,
            "GenerateReport",
            "Generates a formatted report from analysis data. Creates a professional report document with charts and summaries.",

            "{\n" +
            "  \"analysisData\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"The analysis data to include in the report, typically JSON from a previous AnalyzeData capability result.\"\n" +
            "  }\n" +
            "}"
        );
    }
    
    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing GenerateReport with input length: {}", input != null ? input.length() : 0);
        
        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Input analysis data is required to generate report");
        }
        
        try {
            // Simulate report generation
            Thread.sleep(400);
            
            // Generate mock report
            String timestamp = String.valueOf(System.currentTimeMillis());
            String reportPath = "/reports/report_" + timestamp + ".pdf";
            
            String reportData = String.format(
                "{\"report_path\":\"%s\"," +
                "\"format\":\"PDF\"," +
                "\"pages\":5," +
                "\"generated_at\":\"%s\"," +
                "\"summary\":\"Report generated successfully with charts and analysis\"}",
                reportPath, timestamp
            );
            
            String message = String.format("Report generated successfully: %s", reportPath);
            
            return CapabilityResult.success(reportData, message);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CapabilityExecutionException("Report generation interrupted", e);
        }
    }
}

