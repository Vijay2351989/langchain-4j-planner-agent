# Planner Agent - Quick Reference Guide

## PlannerResponse Types

| ID | Type | Method | Meaning | Browser Action |
|----|------|--------|---------|----------------|
| `> 0` | CAPABILITY | `isCapability()` | Execute this capability | Auto-executes capability |
| `0` | CLARIFICATION | `isClarification()` | Need more info | Shows input box |
| `-1` | UNABLE | `isUnableToIdentify()` | Cannot proceed | Shows error |
| `-2` | COMPLETE | `isComplete()` | Task finished | Shows completion |

## WebSocket Endpoints

### Client → Server
- `/app/plan` - Submit user request
- `/app/execute` - Execute a capability
- `/app/clarify` - Provide clarification
- `/app/reset` - Reset session

### Server → Client
- `/topic/response` - All responses

## Key Classes & Methods

### PlannerAgent
```java
// Plan next step
PlannerResponse plan(String userPrompt, List<InputVariable> inputVariables)

// Report result and get next step
PlannerResponse reportAndPlanNext(int capabilityId, String result, int maxResultLength)

// Provide clarification
PlannerResponse provideClarification(String clarification)

// Reset conversation
void reset()
```

### PlannerService
```java
// Get or create agent for session
PlannerAgent getOrCreateAgent(String sessionId, List<Integer> selectedCapabilityIds)

// Execute capability by ID
CapabilityResult executeCapability(int capabilityId, String input)

// Clear session
void clearSession(String sessionId)
```

### CompositeCapability
```java
// Register a method
void registerMethod(String methodId, String name, String description, 
                   CapabilityMethod.MethodExecutor executor)

// Execute specific method
CapabilityResult executeMethod(String methodId, String input)

// Main execute (uses MethodFinder)
CapabilityResult execute(String input)
```

### MethodFinderAgent
```java
// Find appropriate method
MethodFinderResponse findMethod(String capabilityName, String taskDescription, 
                                Map<String, CapabilityMethod> methods)
```

## Capability IDs

| ID | Name | Type | Description |
|----|------|------|-------------|
| 1 | FetchData | Regular | Retrieve data from database |
| 2 | AnalyzeData | Regular | Analyze fetched data |
| 3 | GenerateReport | Regular | Generate reports |
| 4 | SendEmail | Regular | Send email notifications |
| 5 | ExportToFile | Regular | Export data to files |
| 6 | ExecuteScript | Regular | Generate scripts for file ops, system commands, data processing |
| 100 | Mathematics | Composite | 13+ math operations |

**Note:** ExecuteScript should be used for tasks requiring scripts/commands, NOT for simple questions the LLM can answer directly.

## Mathematics Methods

| Method ID | Name | Input Format | Example |
|-----------|------|--------------|---------|
| `add` | Add Numbers | `{"numbers": [a, b, ...]}` | `{"numbers": [10, 20, 30]}` |
| `subtract` | Subtract | `{"a": x, "b": y}` | `{"a": 100, "b": 25}` |
| `multiply` | Multiply | `{"numbers": [a, b, ...]}` | `{"numbers": [5, 6, 7]}` |
| `divide` | Divide | `{"a": x, "b": y}` | `{"a": 100, "b": 4}` |
| `power` | Power | `{"base": x, "exponent": y}` | `{"base": 2, "exponent": 10}` |
| `sqrt` | Square Root | `{"number": x}` | `{"number": 144}` |
| `abs` | Absolute Value | `{"number": x}` | `{"number": -42}` |
| `mean` | Mean/Average | `{"numbers": [a, b, ...]}` | `{"numbers": [10, 20, 30]}` |
| `median` | Median | `{"numbers": [a, b, ...]}` | `{"numbers": [1, 2, 3, 4, 5]}` |
| `sum` | Sum | `{"numbers": [a, b, ...]}` | `{"numbers": [1, 2, 3]}` |
| `sin` | Sine (degrees) | `{"angle": x}` | `{"angle": 90}` |
| `cos` | Cosine (degrees) | `{"angle": x}` | `{"angle": 0}` |
| `tan` | Tangent (degrees) | `{"angle": x}` | `{"angle": 45}` |

## Common Flows

### Regular Capability
```
User Request → Planner → Capability → Result → Planner → Next Step
```

### Composite Capability
```
User Request → Planner → CompositeCapability → MethodFinder → Method → Result → Planner
```

### Clarification Flow
```
User Request → Planner (id=0) → User Input → Planner → Capability
```

## Configuration

### OpenAI Model
```java
OpenAiChatModel.builder()
    .apiKey(apiKey)
    .modelName("gpt-4o-mini")
    .temperature(0.3)
    .responseFormat("json_object")
    .build()
```

### Memory Settings
```java
MessageWindowChatMemory.withMaxMessages(20)  // Default: 20 messages
```

### Result Truncation
```java
reportAndPlanNext(capabilityId, result, 300)  // Truncate to 300 chars
```

## File Locations

### Backend Core
- `src/main/java/com/krista/kme/agent/planner/`
  - `PlannerWebApplication.java` - Main entry point
  - `PlannerAgent.java` - Core planning logic
  - `PlannerResponse.java` - Response structure
  - `MethodFinderAgent.java` - Method selection
  - `MethodFinderResponse.java` - Method selection response
  - `CompositeCapability.java` - Base for composite capabilities
  - `CapabilityMethod.java` - Method representation

### Controllers & Services
- `src/main/java/com/krista/kme/agent/planner/web/`
  - `PlannerController.java` - WebSocket handlers
  - `PlannerService.java` - Session & capability management
  - `WebSocketConfig.java` - WebSocket configuration

### Capabilities
- `src/main/java/com/krista/kme/agent/planner/capabilities/`
  - `FetchDataCapability.java`
  - `AnalyzeDataCapability.java`
  - `GenerateReportCapability.java`
  - `SendEmailCapability.java`
  - `ExportToFileCapability.java`
  - `MathematicsCapability.java` (Composite)

### Frontend
- `src/main/resources/static/`
  - `planner.html` - Main UI
  - `js/planner.js` - WebSocket client & logic
  - `css/planner.css` - Styling

### Tests
- `src/test/java/com/krista/kme/agent/planner/`
  - `MathematicsCapabilityTest.java`
  - `PlannerAgentTest.java`

## Example Requests

### Simple Math
```
"Calculate 10 + 20"
"What is the square root of 144?"
"Divide 100 by 4"
```

### Multi-Step
```
"Fetch sales data for Q4 2024, analyze it, and generate a report"
"Calculate the average of 10, 20, 30, then multiply by 2"
```

### Clarification Trigger
```
"Generate a report"  → Planner asks: "Which data should I use?"
"Analyze the data"   → Planner asks: "Which dataset?"
```

## Debugging Tips

1. **Check logs** - All components log extensively
2. **Watch WebSocket messages** - Use browser dev tools
3. **Inspect PlannerResponse** - Check `id` value to understand response type
4. **Verify capability selection** - Ensure capabilities are selected in UI
5. **Check session** - Each browser tab = separate session
6. **Memory overflow** - If context too large, reduce `maxMessages` or `maxResultLength`

## Common Issues

### Issue: Planner returns id=-1 (Unable)
**Cause:** No capability matches the request
**Solution:** Add appropriate capability or rephrase request

### Issue: Composite capability fails
**Cause:** MethodFinder couldn't select method
**Solution:** Check method descriptions, ensure input is clear

### Issue: Context overflow
**Cause:** Too many messages or large results
**Solution:** Reduce `maxMessages` or `maxResultLength`

### Issue: WebSocket disconnects
**Cause:** Network issues or server restart
**Solution:** Refresh page to reconnect

## Quick Start

1. **Start application**
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Open browser**
   ```
   http://localhost:8080/planner.html
   ```

3. **Select capabilities** (checkboxes)

4. **Enter request** and click "Send"

5. **Watch execution** - Auto-executes capabilities

6. **Reset** - Click "Reset Session" to start over

---

**For detailed flow documentation, see:** `EXECUTION_FLOW.md`

