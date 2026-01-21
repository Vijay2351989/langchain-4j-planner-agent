package com.krista.kme.agent.planner;

/**
 * Abstract base class for executable capabilities.
 *
 * Each capability:
 * - Has an ID, name, and description
 * - Describes its expected input in the description
 * - Implements execute() to perform its action
 * - Returns a CapabilityResult with output data
 *
 * The description should clearly specify:
 * - What the capability does
 * - What input it expects (e.g., "Input: user_id as string")
 * - What output it produces
 *
 * Example:
 * "Fetch user data from database. Input: user_id (string). Output: user details as JSON."
 */
public abstract class Capability {

    private final int id;
    private final String name;
    private final String description;

    /**
     * Create a new capability
     *
     * @param id Unique identifier
     * @param name Capability name
     * @param description Description including expected input format
     */
    public Capability(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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

