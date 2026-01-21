package com.krista.kme.agent.planner;

import java.util.Objects;

/**
 * Represents an input variable that can be provided with a user request.
 * Input variables provide context that the LLM can use when determining
 * capability inputs.
 * 
 * Examples:
 * - name: "user_id", value: "12345"
 * - name: "date_range", value: "2024-Q4"
 * - name: "department", value: "Sales"
 * - name: "format", value: "PDF"
 */
public class InputVariable {
    
    private String name;
    private String value;
    
    /**
     * Default constructor for JSON deserialization
     */
    public InputVariable() {
    }
    
    /**
     * Create a new input variable
     * 
     * @param name The variable name (e.g., "user_id", "date_range")
     * @param value The variable value (e.g., "12345", "2024-Q4")
     */
    public InputVariable(String name, String value) {
        this.name = name;
        this.value = value;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    /**
     * Format this variable for inclusion in a prompt
     * 
     * @return Formatted string like "user_id: 12345"
     */
    public String toPromptString() {
        return name + ": " + value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputVariable that = (InputVariable) o;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
    
    @Override
    public String toString() {
        return "InputVariable{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}

