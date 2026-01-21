package com.krista.kme.agent.planner;

/**
 * Represents a single method within a CompositeCapability.
 * 
 * Each method has:
 * - A unique ID (e.g., "add", "subtract")
 * - A name (e.g., "Add Numbers")
 * - A description of what it does and what parameters it needs
 * - An execute function that performs the operation
 */
public class CapabilityMethod {
    
    private final String id;
    private final String name;
    private final String description;
    private final MethodExecutor executor;
    
    /**
     * Functional interface for method execution
     */
    @FunctionalInterface
    public interface MethodExecutor {
        CapabilityResult execute(String input) throws CapabilityExecutionException;
    }
    
    /**
     * Create a new capability method
     * 
     * @param id Unique method identifier
     * @param name Method name
     * @param description Description including parameters and return value
     * @param executor Function that executes the method
     */
    public CapabilityMethod(String id, String name, String description, MethodExecutor executor) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.executor = executor;
    }
    
    /**
     * Execute this method with the given input
     */
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        return executor.execute(input);
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return String.format("Method{id='%s', name='%s', description='%s'}", id, name, description);
    }
}

