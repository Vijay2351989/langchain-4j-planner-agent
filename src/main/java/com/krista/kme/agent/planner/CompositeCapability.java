package com.krista.kme.agent.planner;

import java.util.HashMap;
import java.util.Map;

/**
 * A capability that contains multiple sub-methods.
 * 
 * This is used for capabilities like Mathematics that have many operations (add, subtract, etc.)
 * Instead of sending all methods to the planner (which would bloat the context), we:
 * 1. Send only the high-level capability description to the planner
 * 2. When the planner selects this capability, use a MethodFinderAgent to find the right method
 * 3. Execute the selected method with the required parameters
 * 
 * Subclasses should:
 * - Register methods in the constructor using registerMethod()
 * - Implement execute() to delegate to MethodFinderAgent
 */
public abstract class CompositeCapability extends Capability {
    
    protected final Map<String, CapabilityMethod> methods;
    
    /**
     * Create a composite capability with input schema
     *
     * @param id Unique identifier
     * @param name Capability name
     * @param description High-level description of what this capability can do
     * @param inputSchema JSON schema describing input fields
     */
    public CompositeCapability(int id, String name, String description, String inputSchema) {
        super(id, name, description, inputSchema);
        this.methods = new HashMap<>();
    }

    /**
     * Create a composite capability without input schema (for backward compatibility)
     *
     * @param id Unique identifier
     * @param name Capability name
     * @param description High-level description of what this capability can do
     */
    public CompositeCapability(int id, String name, String description) {
        this(id, name, description, null);
    }
    
    /**
     * Register a method that this capability can perform
     * 
     * @param methodId Unique method identifier (e.g., "add", "subtract")
     * @param method The method implementation
     */
    protected void registerMethod(String methodId, CapabilityMethod method) {
        methods.put(methodId, method);
    }
    
    /**
     * Get all registered methods
     */
    public Map<String, CapabilityMethod> getMethods() {
        return new HashMap<>(methods);
    }
    
    /**
     * Execute a specific method by ID
     * 
     * @param methodId The method to execute
     * @param input Input parameters for the method
     * @return Result of method execution
     * @throws CapabilityExecutionException if method not found or execution fails
     */
    public CapabilityResult executeMethod(String methodId, String input) throws CapabilityExecutionException {
        CapabilityMethod method = methods.get(methodId);
        if (method == null) {
            throw new CapabilityExecutionException(
                String.format("Method '%s' not found in capability '%s'. Available methods: %s", 
                    methodId, getName(), methods.keySet())
            );
        }
        
        return method.execute(input);
    }
    
    /**
     * Check if this is a composite capability (always true for this class)
     */
    @Override
    public boolean isComposite() {
        return true;
    }
}

