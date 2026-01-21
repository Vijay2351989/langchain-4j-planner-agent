package com.krista.kme.agent.planner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for InputVariable functionality
 */
public class InputVariableTest {
    
    @Test
    public void testInputVariableCreation() {
        InputVariable var = new InputVariable("user_id", "12345");
        
        assertEquals("user_id", var.getName());
        assertEquals("12345", var.getValue());
    }
    
    @Test
    public void testInputVariableToString() {
        InputVariable var = new InputVariable("date_range", "2024-Q4");
        
        String expected = "InputVariable{name='date_range', value='2024-Q4'}";
        assertEquals(expected, var.toString());
    }
    
    @Test
    public void testMultipleVariables() {
        List<InputVariable> variables = new ArrayList<>();
        variables.add(new InputVariable("user_id", "12345"));
        variables.add(new InputVariable("date_range", "2024-Q4"));
        variables.add(new InputVariable("format", "PDF"));
        
        assertEquals(3, variables.size());
        assertEquals("user_id", variables.get(0).getName());
        assertEquals("12345", variables.get(0).getValue());
        assertEquals("date_range", variables.get(1).getName());
        assertEquals("2024-Q4", variables.get(1).getValue());
        assertEquals("format", variables.get(2).getName());
        assertEquals("PDF", variables.get(2).getValue());
    }
    
    @Test
    public void testEmptyVariableList() {
        List<InputVariable> variables = new ArrayList<>();
        
        assertTrue(variables.isEmpty());
        assertEquals(0, variables.size());
    }
    
    @Test
    public void testVariableWithSpecialCharacters() {
        InputVariable var = new InputVariable("email_address", "user@example.com");
        
        assertEquals("email_address", var.getName());
        assertEquals("user@example.com", var.getValue());
    }
    
    @Test
    public void testVariableWithNumericValue() {
        InputVariable var = new InputVariable("count", "100");
        
        assertEquals("count", var.getName());
        assertEquals("100", var.getValue());
    }
    
    @Test
    public void testVariableWithBooleanValue() {
        InputVariable var = new InputVariable("include_headers", "true");
        
        assertEquals("include_headers", var.getName());
        assertEquals("true", var.getValue());
    }
}

