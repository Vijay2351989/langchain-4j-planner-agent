package com.krista.kme.agent.planner.capabilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krista.kme.agent.planner.CapabilityExecutionException;
import com.krista.kme.agent.planner.CapabilityMethod;
import com.krista.kme.agent.planner.CapabilityResult;
import com.krista.kme.agent.planner.CompositeCapability;
import com.krista.kme.agent.planner.MethodFinderAgent;
import com.krista.kme.agent.planner.MethodFinderResponse;

import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * Mathematics capability with multiple operations.
 * 
 * This is a CompositeCapability that contains many mathematical operations.
 * Instead of sending all operations to the planner, we:
 * 1. Send only a high-level description to the planner
 * 2. Use MethodFinderAgent to select the specific operation
 * 3. Execute the selected operation
 * 
 * This approach keeps the planner's context small even with 30-40 operations.
 */
public class MathematicsCapability extends CompositeCapability {
    
    private static final Logger logger = LoggerFactory.getLogger(MathematicsCapability.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final MethodFinderAgent methodFinder;
    
    /**
     * Create Mathematics capability with a method finder agent
     * 
     * @param model Language model for the method finder
     */
    public MathematicsCapability(ChatLanguageModel model) {
        super(
            100, // Capability ID
            "Mathematics",
            "Performs mathematical operations including arithmetic (add, subtract, multiply, divide), " +
            "advanced calculations (power, square root, logarithm), trigonometry (sin, cos, tan), " +
            "and statistics (mean, median, standard deviation). " +
            "This is an intelligent capability that automatically selects the appropriate mathematical operation based on your request.",

            "{\n" +
            "  \"operation\": {\n" +
            "    \"type\": \"string\",\n" +
            "    \"required\": true,\n" +
            "    \"description\": \"A natural language description of the mathematical operation to perform (e.g., 'add 5 and 10', 'calculate the mean of [1, 2, 3, 4, 5]', 'find the square root of 16').\"\n" +
            "  }\n" +
            "}"
        );

        this.methodFinder = new MethodFinderAgent(model);

        // Register all mathematical methods
        registerMethods();

        logger.info("MathematicsCapability initialized with {} methods", methods.size());
    }
    
    /**
     * Register all available mathematical methods
     */
    private void registerMethods() {
        // Basic arithmetic
        registerMethod("add", new CapabilityMethod(
            "add",
            "Add Numbers",
            "Add two or more numbers. Input: JSON with 'numbers' array. Output: Sum.",
            this::add
        ));
        
        registerMethod("subtract", new CapabilityMethod(
            "subtract",
            "Subtract Numbers",
            "Subtract second number from first. Input: JSON with 'a' and 'b'. Output: a - b.",
            this::subtract
        ));
        
        registerMethod("multiply", new CapabilityMethod(
            "multiply",
            "Multiply Numbers",
            "Multiply two or more numbers. Input: JSON with 'numbers' array. Output: Product.",
            this::multiply
        ));
        
        registerMethod("divide", new CapabilityMethod(
            "divide",
            "Divide Numbers",
            "Divide first number by second. Input: JSON with 'a' and 'b'. Output: a / b.",
            this::divide
        ));
        
        // Advanced operations
        registerMethod("power", new CapabilityMethod(
            "power",
            "Power/Exponentiation",
            "Raise a number to a power. Input: JSON with 'base' and 'exponent'. Output: base^exponent.",
            this::power
        ));
        
        registerMethod("sqrt", new CapabilityMethod(
            "sqrt",
            "Square Root",
            "Calculate square root. Input: JSON with 'number'. Output: √number.",
            this::sqrt
        ));
        
        registerMethod("abs", new CapabilityMethod(
            "abs",
            "Absolute Value",
            "Get absolute value. Input: JSON with 'number'. Output: |number|.",
            this::abs
        ));
        
        // Statistics
        registerMethod("mean", new CapabilityMethod(
            "mean",
            "Calculate Mean",
            "Calculate average of numbers. Input: JSON with 'numbers' array. Output: Mean value.",
            this::mean
        ));
        
        registerMethod("median", new CapabilityMethod(
            "median",
            "Calculate Median",
            "Calculate median of numbers. Input: JSON with 'numbers' array. Output: Median value.",
            this::median
        ));
        
        registerMethod("sum", new CapabilityMethod(
            "sum",
            "Sum of Numbers",
            "Calculate sum of all numbers. Input: JSON with 'numbers' array. Output: Total sum.",
            this::sum
        ));
        
        // Trigonometry
        registerMethod("sin", new CapabilityMethod(
            "sin",
            "Sine",
            "Calculate sine of angle in degrees. Input: JSON with 'angle'. Output: sin(angle).",
            this::sin
        ));
        
        registerMethod("cos", new CapabilityMethod(
            "cos",
            "Cosine",
            "Calculate cosine of angle in degrees. Input: JSON with 'angle'. Output: cos(angle).",
            this::cos
        ));
        
        registerMethod("tan", new CapabilityMethod(
            "tan",
            "Tangent",
            "Calculate tangent of angle in degrees. Input: JSON with 'angle'. Output: tan(angle).",
            this::tan
        ));

        // You can add 20-30 more methods here without affecting planner context!
        // Examples: factorial, gcd, lcm, log, ln, ceil, floor, round, max, min, etc.
    }

    @Override
    public CapabilityResult execute(String input) throws CapabilityExecutionException {
        logger.info("Executing Mathematics capability with input: {}", input);

        if (input == null || input.trim().isEmpty()) {
            throw new CapabilityExecutionException("Input is required for Mathematics capability");
        }

        // Use MethodFinderAgent to determine which method to call
        MethodFinderResponse methodResponse = methodFinder.findMethod(getName(), input, methods);

        // Check if method finder needs clarification or cannot proceed
        if (!methodResponse.hasMethod()) {
            String failureMessage = buildMethodFinderFailureMessage(methodResponse);
            logger.warn("Method finder could not identify method: {}", failureMessage);

            // Return failure result with details for the planner to handle
            return CapabilityResult.failure(failureMessage);
        }

        // Check if confidence is too low (parameters uncertain)
        if (methodResponse.needsClarification()) {
            String clarificationMessage = buildClarificationMessage(methodResponse);
            logger.warn("Method finder needs clarification: {}", clarificationMessage);

            // Return failure result asking for clarification
            return CapabilityResult.failure(clarificationMessage);
        }

        logger.info("Method finder selected: {} (confidence: {}) with parameters: {}",
            methodResponse.getMethodId(), methodResponse.getConfidence(), methodResponse.getParameters());

        // Execute the selected method
        return executeMethod(methodResponse.getMethodId(), methodResponse.getParameters());
    }

    /**
     * Build a message explaining why method finder failed
     */
    private String buildMethodFinderFailureMessage(MethodFinderResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("METHOD_FINDER_FAILURE: Could not identify appropriate mathematical operation.\n\n");
        sb.append("Reason: ").append(response.getDescription()).append("\n");

        if (response.getMissingInfo() != null && !response.getMissingInfo().isEmpty()) {
            sb.append("Missing Information: ").append(response.getMissingInfo()).append("\n");
        }

        sb.append("\nAvailable operations: ");
        sb.append(String.join(", ", methods.keySet()));

        return sb.toString();
    }

    /**
     * Build a message requesting clarification
     */
    private String buildClarificationMessage(MethodFinderResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("METHOD_FINDER_NEEDS_CLARIFICATION: ");

        if (response.hasMethod()) {
            sb.append("Identified method '").append(response.getMethodName()).append("' ");
            sb.append("but parameters are uncertain (confidence: ").append(response.getConfidence()).append(").\n\n");
        } else {
            sb.append("Cannot confidently identify the mathematical operation.\n\n");
        }

        sb.append("Issue: ").append(response.getDescription()).append("\n");

        if (response.getMissingInfo() != null && !response.getMissingInfo().isEmpty()) {
            sb.append("Missing Information: ").append(response.getMissingInfo()).append("\n");
        }

        return sb.toString();
    }

    // ========== Method Implementations ==========

    private CapabilityResult add(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            JsonNode numbersNode = json.get("numbers");

            if (numbersNode == null || !numbersNode.isArray()) {
                throw new CapabilityExecutionException("Input must contain 'numbers' array");
            }

            double sum = 0;
            for (JsonNode num : numbersNode) {
                sum += num.asDouble();
            }

            String result = String.valueOf(sum);
            return CapabilityResult.success(result, "Addition completed: " + result);

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to add numbers: " + e.getMessage(), e);
        }
    }

    private CapabilityResult subtract(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double a = json.get("a").asDouble();
            double b = json.get("b").asDouble();
            double result = a - b;

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Subtraction completed: %.2f - %.2f = %.2f", a, b, result)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to subtract: " + e.getMessage(), e);
        }
    }

    private CapabilityResult multiply(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            JsonNode numbersNode = json.get("numbers");

            if (numbersNode == null || !numbersNode.isArray()) {
                throw new CapabilityExecutionException("Input must contain 'numbers' array");
            }

            double product = 1;
            for (JsonNode num : numbersNode) {
                product *= num.asDouble();
            }

            String result = String.valueOf(product);
            return CapabilityResult.success(result, "Multiplication completed: " + result);

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to multiply: " + e.getMessage(), e);
        }
    }

    private CapabilityResult divide(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double a = json.get("a").asDouble();
            double b = json.get("b").asDouble();

            if (b == 0) {
                throw new CapabilityExecutionException("Division by zero is not allowed");
            }

            double result = a / b;

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Division completed: %.2f / %.2f = %.2f", a, b, result)
            );

        } catch (CapabilityExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to divide: " + e.getMessage(), e);
        }
    }

    private CapabilityResult power(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double base = json.get("base").asDouble();
            double exponent = json.get("exponent").asDouble();
            double result = Math.pow(base, exponent);

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Power completed: %.2f^%.2f = %.2f", base, exponent, result)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate power: " + e.getMessage(), e);
        }
    }

    private CapabilityResult sqrt(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double number = json.get("number").asDouble();

            if (number < 0) {
                throw new CapabilityExecutionException("Cannot calculate square root of negative number");
            }

            double result = Math.sqrt(number);

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Square root completed: √%.2f = %.2f", number, result)
            );

        } catch (CapabilityExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate square root: " + e.getMessage(), e);
        }
    }

    private CapabilityResult abs(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double number = json.get("number").asDouble();
            double result = Math.abs(number);

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Absolute value completed: |%.2f| = %.2f", number, result)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate absolute value: " + e.getMessage(), e);
        }
    }

    private CapabilityResult mean(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            JsonNode numbersNode = json.get("numbers");

            if (numbersNode == null || !numbersNode.isArray() || numbersNode.size() == 0) {
                throw new CapabilityExecutionException("Input must contain non-empty 'numbers' array");
            }

            double sum = 0;
            int count = 0;
            for (JsonNode num : numbersNode) {
                sum += num.asDouble();
                count++;
            }

            double result = sum / count;

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Mean calculated: %.2f (from %d numbers)", result, count)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate mean: " + e.getMessage(), e);
        }
    }

    private CapabilityResult median(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            JsonNode numbersNode = json.get("numbers");

            if (numbersNode == null || !numbersNode.isArray() || numbersNode.size() == 0) {
                throw new CapabilityExecutionException("Input must contain non-empty 'numbers' array");
            }

            java.util.List<Double> numbers = new java.util.ArrayList<>();
            for (JsonNode num : numbersNode) {
                numbers.add(num.asDouble());
            }

            java.util.Collections.sort(numbers);

            double result;
            int size = numbers.size();
            if (size % 2 == 0) {
                result = (numbers.get(size / 2 - 1) + numbers.get(size / 2)) / 2.0;
            } else {
                result = numbers.get(size / 2);
            }

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Median calculated: %.2f (from %d numbers)", result, size)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate median: " + e.getMessage(), e);
        }
    }

    private CapabilityResult sum(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            JsonNode numbersNode = json.get("numbers");

            if (numbersNode == null || !numbersNode.isArray()) {
                throw new CapabilityExecutionException("Input must contain 'numbers' array");
            }

            double sum = 0;
            for (JsonNode num : numbersNode) {
                sum += num.asDouble();
            }

            return CapabilityResult.success(
                String.valueOf(sum),
                String.format("Sum calculated: %.2f", sum)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate sum: " + e.getMessage(), e);
        }
    }

    private CapabilityResult sin(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double angle = json.get("angle").asDouble();
            double radians = Math.toRadians(angle);
            double result = Math.sin(radians);

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Sine calculated: sin(%.2f°) = %.4f", angle, result)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate sine: " + e.getMessage(), e);
        }
    }

    private CapabilityResult cos(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double angle = json.get("angle").asDouble();
            double radians = Math.toRadians(angle);
            double result = Math.cos(radians);

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Cosine calculated: cos(%.2f°) = %.4f", angle, result)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate cosine: " + e.getMessage(), e);
        }
    }

    private CapabilityResult tan(String input) throws CapabilityExecutionException {
        try {
            JsonNode json = objectMapper.readTree(input);
            double angle = json.get("angle").asDouble();
            double radians = Math.toRadians(angle);
            double result = Math.tan(radians);

            return CapabilityResult.success(
                String.valueOf(result),
                String.format("Tangent calculated: tan(%.2f°) = %.4f", angle, result)
            );

        } catch (Exception e) {
            throw new CapabilityExecutionException("Failed to calculate tangent: " + e.getMessage(), e);
        }
    }
}
