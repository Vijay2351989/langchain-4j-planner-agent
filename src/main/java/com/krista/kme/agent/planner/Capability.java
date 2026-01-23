package com.krista.kme.agent.planner;

/**
 * Abstract base class for executable capabilities.
 *
 * Each capability:
 * - Has an ID, name, and description
 * - Has an optional input schema that defines expected input fields
 * - Implements execute() to perform its action
 * - Returns a CapabilityResult with output data
 *
 * The description should clearly specify what the capability does.
 * The input schema (if provided) defines the structure of the input JSON.
 *
 * Example:
 * Description: "Fetch user data from database"
 * Input Schema: {"user_id": {"type": "string", "description": "The ID of the user to fetch"}}
 */
public abstract class Capability {

    private final int id;
    private final String name;
    private final String description;
    private final String inputSchema;

    /**
     * Create a new capability with input schema
     *
     * @param id Unique identifier
     * @param name Capability name
     * @param description What the capability does
     * @param inputSchema JSON schema describing input fields (can be null)
     */
    public Capability(int id, String name, String description, String inputSchema) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    /**
     * Create a new capability without input schema (for backward compatibility)
     *
     * @param id Unique identifier
     * @param name Capability name
     * @param description Description including expected input format
     */
    public Capability(int id, String name, String description) {
        this(id, name, description, null);
    }

    /**
     * Execute the capability with the given input.
     *
     * @param input Input data from planner or previous capability
     * @return Result containing output data and status
     * @throws CapabilityExecutionException if execution fails
     */
    public abstract CapabilityResult execute(String input) throws CapabilityExecutionException;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    /**
     * Check if this is a composite capability with multiple methods.
     * Override in CompositeCapability to return true.
     *
     * @return true if this capability contains multiple methods, false otherwise
     */
    public boolean isComposite() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("Capability{id=%d, name='%s', description='%s'}", id, name, description);
    }
}

