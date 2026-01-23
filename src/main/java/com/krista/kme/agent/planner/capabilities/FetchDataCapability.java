package com.krista.kme.agent.planner.capabilities;

import com.krista.kme.agent.planner.Capability;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example capability that fetches data from a database.
 * 
 * Input: query (string) - SQL-like query or search criteria
 * Output: JSON array of records
 */
public class FetchDataCapability extends Capability {
    
    private static final Logger logger = LoggerFactory.getLogger(FetchDataCapability.class);
    
    public FetchDataCapability() {
        super(
            1,
            "FetchData",
            "Fetches data from the database based on a query or search criteria. Returns matching records as a JSON array.",

            "{\n" +
            "  \"query\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"A description of what data to fetch (e.g., 'sales records for Q4 2024', 'all users in the system').\"\n" +
            "  }\n" +
            "}"
        );
    }
    
    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing FetchData with input: {}", input);
        
        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Input query is required");
        }
        
        try {
            // Simulate database fetch
            Thread.sleep(500); // Simulate network delay
            
            // Mock data based on input
            String mockData;
            if (input.toLowerCase().contains("sales")) {
                mockData = "[{\"id\":1,\"product\":\"Widget\",\"amount\":1500,\"date\":\"2024-10-15\"}," +
                          "{\"id\":2,\"product\":\"Gadget\",\"amount\":2300,\"date\":\"2024-11-20\"}," +
                          "{\"id\":3,\"product\":\"Tool\",\"amount\":890,\"date\":\"2024-12-05\"}]";
            } else if (input.toLowerCase().contains("user")) {
                mockData = "[{\"id\":101,\"name\":\"Alice\",\"email\":\"alice@example.com\"}," +
                          "{\"id\":102,\"name\":\"Bob\",\"email\":\"bob@example.com\"}]";
            } else {
                mockData = "[{\"id\":1,\"data\":\"sample\"}]";
            }
            
            String message = String.format("Fetched %d records matching query: %s", 
                countRecords(mockData), input);
            
            return CapabilityResult.success(mockData, message);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CapabilityExecutionException("Fetch interrupted", e);
        }
    }
    
    private int countRecords(String jsonArray) {
        // Simple count by counting opening braces
        return (int) jsonArray.chars().filter(ch -> ch == '{').count();
    }
}

