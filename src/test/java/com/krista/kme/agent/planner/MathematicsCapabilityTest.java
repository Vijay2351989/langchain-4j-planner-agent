package com.krista.kme.agent.planner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.krista.kme.agent.planner.capabilities.MathematicsCapability;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Test for MathematicsCapability and the two-tier system.
 * 
 * Note: These tests require OPENAI_API_KEY to be set.
 * They are integration tests that actually call the LLM.
 */
class MathematicsCapabilityTest {
    
    private ChatLanguageModel model;
    private MathematicsCapability mathCapability;
    
    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            // Skip tests if API key not available
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "OPENAI_API_KEY not set");
        }
        
        model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4")
                .temperature(0.0)
                .build();
        
        mathCapability = new MathematicsCapability(model);
    }
    
    @Test
    void testIsComposite() {
        assertTrue(mathCapability.isComposite(), "MathematicsCapability should be composite");
    }
    
    @Test
    void testHasMultipleMethods() {
        assertTrue(mathCapability.getMethods().size() >= 13, 
            "MathematicsCapability should have at least 13 methods");
    }
    
    @Test
    void testAdditionViaExecute() throws CapabilityExecutionException {
        // Test that the capability can understand a natural language request
        // and route it to the correct method
        String input = "Add the numbers 10, 20, and 30";
        
        CapabilityResult result = mathCapability.execute(input);
        
        assertTrue(result.isSuccess(), "Addition should succeed");
        assertEquals("60.0", result.getOutput(), "Sum should be 60");
    }
    
    @Test
    void testDivisionViaExecute() throws CapabilityExecutionException {
        String input = "Divide 100 by 4";
        
        CapabilityResult result = mathCapability.execute(input);
        
        assertTrue(result.isSuccess(), "Division should succeed");
        assertEquals("25.0", result.getOutput(), "Result should be 25");
    }
    
    @Test
    void testSquareRootViaExecute() throws CapabilityExecutionException {
        String input = "Calculate the square root of 144";
        
        CapabilityResult result = mathCapability.execute(input);
        
        assertTrue(result.isSuccess(), "Square root should succeed");
        assertEquals("12.0", result.getOutput(), "Square root of 144 should be 12");
    }
    
    @Test
    void testDirectMethodExecution() throws CapabilityExecutionException {
        // Test executing a method directly with JSON parameters
        String jsonInput = "{\"numbers\": [5, 10, 15]}";
        
        CapabilityResult result = mathCapability.executeMethod("add", jsonInput);
        
        assertTrue(result.isSuccess(), "Direct method execution should succeed");
        assertEquals("30.0", result.getOutput(), "Sum should be 30");
    }
    
    @Test
    void testInvalidMethodId() {
        assertThrows(CapabilityExecutionException.class, () -> {
            mathCapability.executeMethod("nonexistent", "{}");
        }, "Should throw exception for invalid method ID");
    }
    
    @Test
    void testMeanCalculation() throws CapabilityExecutionException {
        String input = "Calculate the average of 10, 20, 30, 40, 50";
        
        CapabilityResult result = mathCapability.execute(input);
        
        assertTrue(result.isSuccess(), "Mean calculation should succeed");
        assertEquals("30.0", result.getOutput(), "Mean should be 30");
    }
    
    @Test
    void testPowerCalculation() throws CapabilityExecutionException {
        String input = "What is 2 to the power of 10?";
        
        CapabilityResult result = mathCapability.execute(input);
        
        assertTrue(result.isSuccess(), "Power calculation should succeed");
        assertEquals("1024.0", result.getOutput(), "2^10 should be 1024");
    }
    
    @Test
    void testTrigonometry() throws CapabilityExecutionException {
        String input = "Calculate sine of 90 degrees";
        
        CapabilityResult result = mathCapability.execute(input);
        
        assertTrue(result.isSuccess(), "Sine calculation should succeed");
        // sin(90°) = 1.0
        double resultValue = Double.parseDouble(result.getOutput());
        assertEquals(1.0, resultValue, 0.0001, "sin(90°) should be approximately 1.0");
    }
    
    @Test
    void testDivisionByZero() {
        String jsonInput = "{\"a\": 10, \"b\": 0}";
        
        assertThrows(CapabilityExecutionException.class, () -> {
            mathCapability.executeMethod("divide", jsonInput);
        }, "Should throw exception for division by zero");
    }
    
    @Test
    void testNegativeSquareRoot() {
        String jsonInput = "{\"number\": -25}";
        
        assertThrows(CapabilityExecutionException.class, () -> {
            mathCapability.executeMethod("sqrt", jsonInput);
        }, "Should throw exception for square root of negative number");
    }
}

