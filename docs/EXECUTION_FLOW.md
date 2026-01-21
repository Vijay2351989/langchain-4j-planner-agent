# Planner Agent Web UI - Complete Execution Flow

This document provides a comprehensive mental model of how the Planner Agent system works from user request to final result.

---

## Table of Contents

1. [System Architecture Overview](#system-architecture-overview)
2. [Key Components](#key-components)
3. [Flow 1: Regular Capability Execution](#flow-1-regular-capability-execution)
4. [Flow 2: Composite Capability Execution](#flow-2-composite-capability-execution)
5. [Response Types](#response-types)
6. [WebSocket Communication](#websocket-communication)
7. [Session Management](#session-management)
8. [Key Files Reference](#key-files-reference)

---

## System Architecture Overview

```
┌─────────────┐
│   Browser   │
│   (Web UI)  │
└──────┬──────┘
       │ WebSocket (STOMP)
       │
┌──────▼──────────────────────────────────────────────────┐
│              Spring Boot Backend                        │
│  ┌────────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │ PlannerController│→│PlannerService│→│PlannerAgent │ │
│  └────────────────┘  └──────────────┘  └─────────────┘ │
│                                              │          │
│                                              ▼          │
│                      ┌──────────────────────────────┐   │
│                      │   OpenAI GPT-4o-mini        │   │
│                      │   (via LangChain4j)         │   │
│                      └──────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │           Capabilities Registry                  │   │
│  │  • FetchData (ID: 1)                            │   │
│  │  • AnalyzeData (ID: 2)                          │   │
│  │  • GenerateReport (ID: 3)                       │   │
│  │  • SendEmail (ID: 4)                            │   │
│  │  • ExportToFile (ID: 5)                         │   │
│  │  • Mathematics (ID: 100) ← Composite            │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

---

## Key Components

### 1. **PlannerController** 
**File:** `src/main/java/com/krista/kme/agent/planner/web/PlannerController.java`

**Responsibility:** Handles WebSocket messages from the browser

**Key Methods:**
- `plan(Map<String, Object> request)` - Initial user request
- `execute(Map<String, Object> request)` - Execute a capability
- `clarify(Map<String, String> request)` - Provide clarification
- `reset(Map<String, String> request)` - Reset session

---

### 2. **PlannerService**
**File:** `src/main/java/com/krista/kme/agent/planner/web/PlannerService.java`

**Responsibility:** Manages PlannerAgent instances and capabilities

**Key Methods:**
- `getOrCreateAgent(String sessionId, List<Integer> selectedCapabilityIds)` - Get/create agent for session
- `executeCapability(int capabilityId, String input)` - Execute a capability by ID

**Initialization:**
```java
public PlannerService() {
    // 1. Initialize OpenAI chat model
    this.model = OpenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName("gpt-4o-mini")
        .temperature(0.3)
        .responseFormat("json_object")
        .build();
    
    // 2. Create capabilities (including MathematicsCapability)
    this.capabilities = createCapabilities();
}
```

---

### 3. **PlannerAgent**
**File:** `src/main/java/com/krista/kme/agent/planner/PlannerAgent.java`

**Responsibility:** Core planning logic using LLM

**Key Methods:**

#### `plan(String userPrompt, List<InputVariable> inputVariables)`
**Input:**
- `userPrompt` - User's natural language request
- `inputVariables` - Optional context variables

**Output:** `PlannerResponse` object

**What it does:**
1. Builds complete prompt with input variables
2. Sends to LLM via LangChain4j AI Service
3. LLM returns structured JSON matching `PlannerResponse` schema
4. Returns parsed `PlannerResponse` object

#### `reportAndPlanNext(int capabilityId, String result, int maxResultLength)`
**Input:**
- `capabilityId` - ID of capability that just executed
- `result` - Result from capability execution
- `maxResultLength` - Max chars to include (default: 500)

**Output:** `PlannerResponse` object

**What it does:**
1. Truncates result if too long (prevents context overflow)
2. Builds update prompt: "I completed capability X. Result: Y. What next?"
3. Calls `plan()` with this update
4. Returns next step from planner

---

### 4. **PlannerResponse**
**File:** `src/main/java/com/krista/kme/agent/planner/PlannerResponse.java`

**Structure:**
```java
{
    "id": int,           // Capability ID or special code
    "name": String,      // Capability/response name
    "description": String, // Details/explanation
    "input": String      // Input data for capability
}
```

**Response Types:**
- `id > 0` → Execute capability (e.g., id=100 for Mathematics)
- `id = 0` → Need clarification
- `id = -1` → Unable to proceed
- `id = -2` → Task complete

**Helper Methods:**
- `isCapability()` - Returns `id > 0`
- `isClarification()` - Returns `id == 0`
- `isUnableToIdentify()` - Returns `id == -1`
- `isComplete()` - Returns `id == -2`

---

## Flow 1: Regular Capability Execution

**Example:** User asks "Fetch sales data for Q4 2024"

### Step-by-Step Flow

#### 1. **User Submits Request** (Browser)
```javascript
// planner.js
stompClient.send('/app/plan', {}, JSON.stringify({
    sessionId: "abc123",
    request: "Fetch sales data for Q4 2024",
    selectedCapabilities: [1, 2, 3, 4, 5, 100]
}));
```

#### 2. **Controller Receives Request**
```java
// PlannerController.plan()
@MessageMapping("/app/plan")
public Map<String, Object> plan(Map<String, Object> request) {
    String sessionId = request.get("sessionId");           // "abc123"
    String userRequest = request.get("request");           // "Fetch sales data..."
    List<Integer> selectedCaps = parseSelectedCapabilities(...); // [1,2,3,4,5,100]
    
    // Get or create agent for this session
    PlannerAgent agent = plannerService.getOrCreateAgent(sessionId, selectedCaps);
    
    // Ask planner what to do
    PlannerResponse response = agent.plan(userRequest, null);
    
    return createResponseMap("planner_response", response, null);
}
```

#### 3. **PlannerService Creates Agent**
```java
// PlannerService.getOrCreateAgent()
public PlannerAgent getOrCreateAgent(String sessionId, List<Integer> selectedCapabilityIds) {
    // Filter capabilities based on user selection
    Map<Integer, Capability> filteredCaps = new HashMap<>();
    for (Integer id : selectedCapabilityIds) {
        filteredCaps.put(id, capabilities.get(id));
    }
    
    // Create new agent with filtered capabilities
    return new PlannerAgent(model, systemPrompt, filteredCaps);
}
```

#### 4. **PlannerAgent Calls LLM**
```java
// PlannerAgent.plan()
public PlannerResponse plan(String userPrompt, List<InputVariable> inputVariables) {
    // Build complete prompt
    String completePrompt = buildPromptWithVariables(userPrompt, inputVariables);
    
    // Call LLM via LangChain4j AI Service
    // System message already in memory with capabilities list
    PlannerResponse response = plannerService.plan(completePrompt);
    
    return response;
}
```

**System Message (sent once at initialization):**
```
You are a task planner. Break down user requests into sequential capability executions.

AVAILABLE CAPABILITIES:
1. FetchData - Retrieve data from database
2. AnalyzeData - Analyze the fetched data
3. GenerateReport - Generate a report
4. SendEmail - Send email notifications
5. ExportToFile - Export data to files
100. Mathematics - Perform mathematical operations...

RESPONSE RULES:
1. If you can identify next capability: return id, name, description, input
2. If you need clarification: return id=0
3. If task complete: return id=-2
4. If cannot proceed: return id=-1
```

**LLM Response (JSON):**
```json
{
    "id": 1,
    "name": "FetchData",
    "description": "Fetching sales data for Q4 2024",
    "input": "SELECT * FROM sales WHERE quarter='Q4' AND year=2024"
}
```

#### 5. **Response Sent to Browser**
```java
// PlannerController returns
{
    "type": "planner_response",
    "data": {
        "id": 1,
        "name": "FetchData",
        "description": "Fetching sales data for Q4 2024",
        "input": "SELECT * FROM sales WHERE quarter='Q4' AND year=2024"
    }
}
```

#### 6. **Browser Displays & Auto-Executes**
```javascript
// planner.js - handlePlannerResponse()
function handlePlannerResponse(data) {
    if (data.id > 0) {  // It's a capability
        addMessage('planner', data.name, data.description);
        
        // Auto-execute the capability
        executeCapability(data.id, data.input);
    }
}
```

#### 7. **Execute Capability**
```javascript
// Browser sends execute request
stompClient.send('/app/execute', {}, JSON.stringify({
    sessionId: "abc123",
    capabilityId: 1,
    input: "SELECT * FROM sales WHERE quarter='Q4' AND year=2024"
}));
```

#### 8. **Controller Executes Capability**
```java
// PlannerController.execute()
@MessageMapping("/execute")
public Map<String, Object> execute(Map<String, Object> request) {
    Integer capabilityId = request.get("capabilityId");  // 1
    String input = request.get("input");                 // SQL query
    
    // Execute the capability
    CapabilityResult result = plannerService.executeCapability(capabilityId, input);
    
    // Send execution result to browser
    messagingTemplate.convertAndSend("/topic/response", 
        createResponseMap("execution_result", result, null));
    
    // If successful, ask planner what's next
    if (result.isSuccess()) {
        PlannerAgent agent = plannerService.getOrCreateAgent(sessionId);
        PlannerResponse nextResponse = agent.reportAndPlanNext(
            capabilityId,
            result.toReportString(),
            300  // Truncate to 300 chars
        );
        
        return createResponseMap("planner_response", nextResponse, null);
    }
}
```

#### 9. **PlannerService Executes Capability**
```java
// PlannerService.executeCapability()
public CapabilityResult executeCapability(int capabilityId, String input) {
    Capability capability = capabilities.get(capabilityId);  // Get FetchDataCapability

    // Execute it
    return capability.execute(input);
}
```

#### 10. **Capability Executes**
```java
// FetchDataCapability.execute()
public CapabilityResult execute(String input) {
    // Simulate fetching data
    String data = fetchFromDatabase(input);

    return CapabilityResult.success(
        data,
        "Successfully fetched 150 records"
    );
}
```

**Returns:**
```java
CapabilityResult {
    success: true,
    output: "[{id:1, amount:5000}, {id:2, amount:7500}, ...]",
    message: "Successfully fetched 150 records"
}
```

#### 11. **Report Back to Planner**
```java
// PlannerAgent.reportAndPlanNext()
public PlannerResponse reportAndPlanNext(int capabilityId, String result, int maxResultLength) {
    // Truncate if needed
    String truncatedResult = result.length() > 300
        ? result.substring(0, 300) + "... (truncated)"
        : result;

    // Build update prompt
    String updatePrompt = String.format(
        "I have completed executing capability '%s' (ID: %d). Result: %s\n\nWhat should I do next?",
        "FetchData", 1, truncatedResult
    );

    // Ask planner for next step
    return plan(updatePrompt);
}
```

**LLM sees in conversation history:**
```
User: Fetch sales data for Q4 2024
Assistant: {"id": 1, "name": "FetchData", ...}
User: I completed FetchData (ID: 1). Result: Successfully fetched 150 records. What next?
```

**LLM Response:**
```json
{
    "id": 2,
    "name": "AnalyzeData",
    "description": "Analyzing the fetched sales data",
    "input": "Analyze sales trends and identify top performers"
}
```

#### 12. **Loop Continues**
- Browser receives next `PlannerResponse` (AnalyzeData)
- Auto-executes AnalyzeData capability
- Reports back to planner
- Planner might select GenerateReport next
- Eventually returns `id: -2` (COMPLETE)

---

## Flow 2: Composite Capability Execution

**Example:** User asks "Calculate the sum of 15, 23, and 42"

This flow demonstrates the **two-tier architecture** for composite capabilities.

### Architecture Difference

**Regular Capability:**
```
User → Planner → Capability → Result
```

**Composite Capability:**
```
User → Planner → CompositeCapability → MethodFinder → Method → Result
                      ↓
                 (13+ methods)
```

### Why Two-Tier?

**Problem:** Mathematics has 13+ operations (add, subtract, multiply, divide, sqrt, power, sin, cos, tan, mean, median, sum, abs). Sending all to planner = huge context.

**Solution:**
- Planner sees only 1 high-level capability: "Mathematics - Perform mathematical operations..."
- When Mathematics is selected, a **MethodFinderAgent** (sub-agent) selects the specific operation
- Keeps planner context small, allows unlimited methods

### Step-by-Step Flow

#### 1. **User Submits Request**
```javascript
stompClient.send('/app/plan', {}, JSON.stringify({
    sessionId: "xyz789",
    request: "Calculate the sum of 15, 23, and 42",
    selectedCapabilities: [1, 2, 3, 4, 5, 100]
}));
```

#### 2. **Planner Sees Only High-Level Description**

**System Message includes:**
```
100. Mathematics - Perform mathematical operations including arithmetic (add, subtract,
multiply, divide), advanced calculations (power, square root), trigonometry (sin, cos, tan),
statistics (mean, median), and more. Input: Description of the mathematical operation needed
with numbers. Output: Result of the calculation.
```

**Note:** Planner does NOT see individual methods (add, subtract, etc.)

#### 3. **Planner Selects Mathematics Capability**

**LLM Response:**
```json
{
    "id": 100,
    "name": "Mathematics",
    "description": "Calculating the sum of 15, 23, and 42",
    "input": "Calculate the sum of 15, 23, and 42"
}
```

#### 4. **Browser Auto-Executes**
```javascript
executeCapability(100, "Calculate the sum of 15, 23, and 42");
```

#### 5. **Controller Executes Composite Capability**
```java
// PlannerController.execute()
CapabilityResult result = plannerService.executeCapability(100, "Calculate the sum of 15, 23, and 42");
```

#### 6. **MathematicsCapability.execute() - The Magic Happens Here**

**File:** `src/main/java/com/krista/kme/agent/planner/capabilities/MathematicsCapability.java`

```java
@Override
public CapabilityResult execute(String input) throws CapabilityExecutionException {
    logger.info("Executing Mathematics capability with input: {}", input);

    // STEP 1: Use MethodFinderAgent to determine which method to call
    MethodFinderResponse methodResponse = methodFinder.findMethod(
        getName(),      // "Mathematics"
        input,          // "Calculate the sum of 15, 23, and 42"
        methods         // Map of all 13+ methods
    );

    if (!methodResponse.hasMethod()) {
        throw new CapabilityExecutionException(
            "Could not identify appropriate mathematical operation"
        );
    }

    logger.info("Method finder selected: {} with parameters: {}",
        methodResponse.getMethodId(),      // "add"
        methodResponse.getParameters());   // {"numbers": [15, 23, 42]}

    // STEP 2: Execute the selected method
    return executeMethod(
        methodResponse.getMethodId(),      // "add"
        methodResponse.getParameters()     // {"numbers": [15, 23, 42]}
    );
}
```

#### 7. **MethodFinderAgent Selects Method**

**File:** `src/main/java/com/krista/kme/agent/planner/MethodFinderAgent.java`

```java
public MethodFinderResponse findMethod(
    String capabilityName,           // "Mathematics"
    String taskDescription,          // "Calculate the sum of 15, 23, and 42"
    Map<String, CapabilityMethod> methods  // All 13+ methods
) {
    // Build prompt for method selection
    String prompt = buildPrompt(capabilityName, taskDescription, methods);

    // Call LLM to select method
    MethodFinderResponse response = methodFinderService.findMethod(prompt);

    return response;
}
```

**Prompt sent to LLM:**
```
You are a method selector for the 'Mathematics' capability.

TASK DESCRIPTION:
Calculate the sum of 15, 23, and 42

AVAILABLE METHODS:
- ID: add | Name: Add Numbers | Description: Add two or more numbers. Input: JSON with 'numbers' array
- ID: subtract | Name: Subtract Numbers | Description: Subtract second from first. Input: JSON with 'a' and 'b'
- ID: multiply | Name: Multiply Numbers | Description: Multiply two or more numbers. Input: JSON with 'numbers' array
- ID: divide | Name: Divide Numbers | Description: Divide first by second. Input: JSON with 'a' and 'b'
- ID: power | Name: Power | Description: Raise to power. Input: JSON with 'base' and 'exponent'
- ID: sqrt | Name: Square Root | Description: Calculate square root. Input: JSON with 'number'
- ID: abs | Name: Absolute Value | Description: Get absolute value. Input: JSON with 'number'
- ID: mean | Name: Calculate Mean | Description: Calculate average. Input: JSON with 'numbers' array
- ID: median | Name: Calculate Median | Description: Calculate median. Input: JSON with 'numbers' array
- ID: sum | Name: Sum of Numbers | Description: Sum all numbers. Input: JSON with 'numbers' array
- ID: sin | Name: Sine | Description: Calculate sine in degrees. Input: JSON with 'angle'
- ID: cos | Name: Cosine | Description: Calculate cosine in degrees. Input: JSON with 'angle'
- ID: tan | Name: Tangent | Description: Calculate tangent in degrees. Input: JSON with 'angle'

YOUR JOB:
1. Analyze the task description
2. Select the most appropriate method
3. Extract parameters from task description
4. Return: methodId, methodName, description, parameters (as JSON string)
```

**LLM Response (MethodFinderResponse):**
```json
{
    "methodId": "add",
    "methodName": "Add Numbers",
    "description": "Selected add method to sum the three numbers",
    "parameters": "{\"numbers\": [15, 23, 42]}"
}
```

#### 8. **Execute Selected Method**

**File:** `src/main/java/com/krista/kme/agent/planner/CompositeCapability.java`

```java
public CapabilityResult executeMethod(String methodId, String input) {
    CapabilityMethod method = methods.get(methodId);  // Get "add" method

    if (method == null) {
        throw new CapabilityExecutionException("Method not found: " + methodId);
    }

    return method.execute(input);  // Execute add method
}
```

#### 9. **Add Method Executes**

**File:** `src/main/java/com/krista/kme/agent/planner/capabilities/MathematicsCapability.java`

```java
private CapabilityResult add(String input) throws CapabilityExecutionException {
    try {
        // Parse JSON input
        JsonNode json = objectMapper.readTree(input);
        JsonNode numbersNode = json.get("numbers");  // [15, 23, 42]

        if (numbersNode == null || !numbersNode.isArray()) {
            throw new CapabilityExecutionException("Input must contain 'numbers' array");
        }

        // Calculate sum
        double sum = 0;
        for (JsonNode num : numbersNode) {
            sum += num.asDouble();  // 15 + 23 + 42 = 80
        }

        String result = String.valueOf(sum);  // "80.0"
        return CapabilityResult.success(result, "Addition completed: " + result);

    } catch (Exception e) {
        throw new CapabilityExecutionException("Failed to add numbers: " + e.getMessage(), e);
    }
}
```

**Returns:**
```java
CapabilityResult {
    success: true,
    output: "80.0",
    message: "Addition completed: 80.0"
}
```

#### 10. **Result Flows Back**

**Execution Stack Unwinds:**
```
add() returns CapabilityResult
    ↓
executeMethod() returns CapabilityResult
    ↓
MathematicsCapability.execute() returns CapabilityResult
    ↓
PlannerService.executeCapability() returns CapabilityResult
    ↓
PlannerController.execute() receives CapabilityResult
```

#### 11. **Controller Sends Result & Gets Next Step**
```java
// Send execution result to browser
messagingTemplate.convertAndSend("/topic/response", {
    "type": "execution_result",
    "data": {
        "success": true,
        "output": "80.0",
        "message": "Addition completed: 80.0"
    }
});

// Ask planner what's next
PlannerResponse nextResponse = agent.reportAndPlanNext(
    100,  // Mathematics capability ID
    "Addition completed: 80.0",
    300
);
```

**Planner sees:**
```
User: Calculate the sum of 15, 23, and 42
Assistant: {"id": 100, "name": "Mathematics", ...}
User: I completed Mathematics (ID: 100). Result: Addition completed: 80.0. What next?
```

**Planner Response:**
```json
{
    "id": -2,
    "name": "Complete",
    "description": "Task completed. The sum of 15, 23, and 42 is 80.0",
    "input": null
}
```

#### 12. **Browser Displays Completion**
```javascript
function handlePlannerResponse(data) {
    if (data.id === -2) {  // COMPLETE
        addMessage('complete', 'Task Complete', data.description);
        // "Task completed. The sum of 15, 23, and 42 is 80.0"
    }
}
```

---

## Response Types

### PlannerResponse Types

**File:** `src/main/java/com/krista/kme/agent/planner/PlannerResponse.java`

| ID Value | Type | Method | Meaning | Next Action |
|----------|------|--------|---------|-------------|
| `> 0` | CAPABILITY | `isCapability()` | Execute this capability | Browser auto-executes |
| `0` | CLARIFICATION | `isClarification()` | Need more info | Browser prompts user |
| `-1` | UNABLE | `isUnableToIdentify()` | Cannot proceed | Browser shows error |
| `-2` | COMPLETE | `isComplete()` | Task done | Browser shows completion |

### Example Responses

#### 1. Capability Execution
```json
{
    "id": 100,
    "name": "Mathematics",
    "description": "Calculating square root of 144",
    "input": "Calculate the square root of 144"
}
```

#### 2. Clarification Request
```json
{
    "id": 0,
    "name": "Clarification",
    "description": "Which quarter do you want to analyze? Q1, Q2, Q3, or Q4?",
    "input": null
}
```

**Browser shows input box for user to respond**

#### 3. Task Complete
```json
{
    "id": -2,
    "name": "Complete",
    "description": "Report has been generated and emailed to john@example.com",
    "input": null
}
```

#### 4. Unable to Proceed
```json
{
    "id": -1,
    "name": "Unable",
    "description": "I don't have a capability to delete database records",
    "input": null
}
```

---

## WebSocket Communication

### Configuration

**File:** `src/main/java/com/krista/kme/agent/planner/web/WebSocketConfig.java`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");           // Server → Client
        config.setApplicationDestinationPrefixes("/app"); // Client → Server
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();  // WebSocket endpoint
    }
}
```

### Message Flow

#### Client → Server (Request)
```
Endpoint: /app/plan
Endpoint: /app/execute
Endpoint: /app/clarify
Endpoint: /app/reset
```

#### Server → Client (Response)
```
Topic: /topic/response
```

### Browser Connection

**File:** `src/main/resources/static/js/planner.js`

```javascript
// Connect to WebSocket
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    // Subscribe to responses
    stompClient.subscribe('/topic/response', function(message) {
        const response = JSON.parse(message.body);
        handleResponse(response);
    });
});

// Send request
function sendPlanRequest(userRequest) {
    stompClient.send('/app/plan', {}, JSON.stringify({
        sessionId: sessionId,
        request: userRequest,
        selectedCapabilities: getSelectedCapabilities()
    }));
}
```

---

## Session Management

### Session Lifecycle

**File:** `src/main/java/com/krista/kme/agent/planner/web/PlannerService.java`

```java
public class PlannerService {
    // Session ID → PlannerAgent instance
    private final Map<String, PlannerAgent> sessions = new ConcurrentHashMap<>();

    // Session ID → Selected capability IDs
    private final Map<String, List<Integer>> sessionCapabilities = new ConcurrentHashMap<>();
}
```

### Session Creation

```java
public PlannerAgent getOrCreateAgent(String sessionId, List<Integer> selectedCapabilityIds) {
    // Store capability selection
    if (selectedCapabilityIds != null) {
        sessionCapabilities.put(sessionId, new ArrayList<>(selectedCapabilityIds));
    }

    // Create agent if doesn't exist
    return sessions.computeIfAbsent(sessionId, id -> {
        // Filter capabilities based on selection
        Map<Integer, Capability> filteredCaps = new HashMap<>();
        for (Integer capId : selectedCapabilityIds) {
            filteredCaps.put(capId, capabilities.get(capId));
        }

        // Create agent with filtered capabilities
        return new PlannerAgent(model, systemPrompt, filteredCaps);
    });
}
```

### Conversation Memory

**File:** `src/main/java/com/krista/kme/agent/planner/PlannerAgent.java`

```java
public PlannerAgent(ChatLanguageModel model, String systemPrompt,
                    Map<Integer, Capability> capabilities, int maxMessages) {
    // Create memory with message window (default: 20 messages)
    this.memory = MessageWindowChatMemory.withMaxMessages(maxMessages);

    // Add system message once
    String systemMessageContent = buildSystemMessage();
    memory.add(SystemMessage.from(systemMessageContent));
}
```

**Memory Contents Over Time:**
```
[System Message] - Capabilities list + rules
[User] - "Fetch sales data for Q4 2024"
[Assistant] - {"id": 1, "name": "FetchData", ...}
[User] - "I completed FetchData. Result: 150 records. What next?"
[Assistant] - {"id": 2, "name": "AnalyzeData", ...}
[User] - "I completed AnalyzeData. Result: Top product is Widget A. What next?"
[Assistant] - {"id": 3, "name": "GenerateReport", ...}
...
```

### Session Reset

```java
public void clearSession(String sessionId) {
    sessions.remove(sessionId);
    sessionCapabilities.remove(sessionId);
}
```

**Browser triggers:**
```javascript
stompClient.send('/app/reset', {}, JSON.stringify({
    sessionId: sessionId
}));
```

---

## Key Files Reference

### Backend Core

| File | Purpose | Key Methods |
|------|---------|-------------|
| `PlannerWebApplication.java` | Spring Boot entry point | `main()` |
| `WebSocketConfig.java` | WebSocket configuration | `configureMessageBroker()` |
| `PlannerController.java` | WebSocket message handler | `plan()`, `execute()`, `clarify()`, `reset()` |
| `PlannerService.java` | Agent & capability manager | `getOrCreateAgent()`, `executeCapability()` |
| `PlannerAgent.java` | Core planning logic | `plan()`, `reportAndPlanNext()` |
| `PlannerResponse.java` | Structured response POJO | `isCapability()`, `isComplete()`, etc. |

### Capabilities

| File | Type | ID | Methods |
|------|------|----|---------|
| `FetchDataCapability.java` | Regular | 1 | `execute()` |
| `AnalyzeDataCapability.java` | Regular | 2 | `execute()` |
| `GenerateReportCapability.java` | Regular | 3 | `execute()` |
| `SendEmailCapability.java` | Regular | 4 | `execute()` |
| `ExportToFileCapability.java` | Regular | 5 | `execute()` |
| `MathematicsCapability.java` | Composite | 100 | `execute()`, 13+ math methods |

### Composite Capability Framework

| File | Purpose | Key Methods |
|------|---------|-------------|
| `CompositeCapability.java` | Base class for composite capabilities | `registerMethod()`, `executeMethod()` |
| `CapabilityMethod.java` | Represents a single method | `execute()` |
| `MethodFinderAgent.java` | Sub-agent for method selection | `findMethod()` |
| `MethodFinderResponse.java` | Method selection response | `hasMethod()` |

### Frontend

| File | Purpose |
|------|---------|
| `planner.html` | Main UI page |
| `planner.js` | WebSocket client & UI logic |
| `planner.css` | Styling |

---

## Execution Flow Diagrams

### Regular Capability Flow

```
┌─────────┐
│ Browser │
└────┬────┘
     │ 1. Send request via WebSocket
     │    /app/plan
     ▼
┌──────────────────┐
│PlannerController │
└────┬─────────────┘
     │ 2. Get/create agent
     ▼
┌──────────────┐
│PlannerService│
└────┬─────────┘
     │ 3. Filter capabilities
     ▼
┌─────────────┐
│PlannerAgent │
└────┬────────┘
     │ 4. Call LLM
     ▼
┌──────────────┐
│ OpenAI LLM   │
└────┬─────────┘
     │ 5. Return PlannerResponse
     ▼
┌─────────┐
│ Browser │ ← Display & auto-execute
└────┬────┘
     │ 6. Execute capability
     │    /app/execute
     ▼
┌──────────────────┐
│PlannerController │
└────┬─────────────┘
     │ 7. Execute capability
     ▼
┌──────────────┐
│PlannerService│
└────┬─────────┘
     │ 8. Get capability by ID
     ▼
┌────────────────┐
│   Capability   │ (e.g., FetchDataCapability)
└────┬───────────┘
     │ 9. Execute & return result
     ▼
┌──────────────────┐
│PlannerController │
└────┬─────────────┘
     │ 10. Send result to browser
     │ 11. Ask planner for next step
     ▼
┌─────────────┐
│PlannerAgent │
└────┬────────┘
     │ 12. reportAndPlanNext()
     ▼
┌──────────────┐
│ OpenAI LLM   │
└────┬─────────┘
     │ 13. Return next PlannerResponse
     ▼
┌─────────┐
│ Browser │ ← Loop continues or completes
└─────────┘
```

### Composite Capability Flow (Mathematics)

```
┌─────────┐
│ Browser │
└────┬────┘
     │ Execute Mathematics capability
     │ /app/execute (id=100, input="Calculate sum of 15, 23, 42")
     ▼
┌──────────────────┐
│PlannerController │
└────┬─────────────┘
     │
     ▼
┌──────────────┐
│PlannerService│
└────┬─────────┘
     │ Get capability ID 100
     ▼
┌──────────────────────┐
│MathematicsCapability │
│  (CompositeCapability)│
└────┬─────────────────┘
     │ execute(input)
     │
     │ ┌─────────────────────────────────┐
     │ │ STEP 1: Find Method             │
     │ └─────────────────────────────────┘
     ▼
┌──────────────────┐
│MethodFinderAgent │
└────┬─────────────┘
     │ findMethod("Mathematics", input, 13+ methods)
     ▼
┌──────────────┐
│ OpenAI LLM   │ ← Sees all 13+ methods
└────┬─────────┘
     │ Returns: methodId="add", parameters={"numbers":[15,23,42]}
     ▼
┌──────────────────────┐
│MethodFinderResponse │
└────┬─────────────────┘
     │
     │ ┌─────────────────────────────────┐
     │ │ STEP 2: Execute Method          │
     │ └─────────────────────────────────┘
     ▼
┌──────────────────────┐
│MathematicsCapability │
└────┬─────────────────┘
     │ executeMethod("add", parameters)
     ▼
┌──────────────────┐
│CapabilityMethod  │ (add method)
└────┬─────────────┘
     │ Parse JSON: [15, 23, 42]
     │ Calculate: 15 + 23 + 42 = 80
     ▼
┌──────────────────┐
│CapabilityResult  │
│ output: "80.0"   │
└────┬─────────────┘
     │ Returns up the stack
     ▼
┌─────────┐
│ Browser │ ← Displays result
└─────────┘
```

---

## Summary

### Key Takeaways

1. **Two-Tier Architecture**: Composite capabilities use a sub-agent (MethodFinder) to select specific methods, keeping planner context small

2. **Structured Output**: LangChain4j automatically converts POJO classes (`PlannerResponse`, `MethodFinderResponse`) to JSON schemas for LLM

3. **Session Management**: Each browser session gets its own `PlannerAgent` instance with conversation memory

4. **WebSocket Communication**: Real-time bidirectional communication between browser and server

5. **Capability Selection**: Users can select which capabilities are available to the planner

6. **Automatic Execution**: Browser automatically executes capabilities selected by planner

7. **Conversation Loop**:
   - User request → Planner selects capability → Execute → Report result → Planner selects next → Repeat until complete

8. **Response Types**: Planner can return capability execution, clarification request, completion, or unable to proceed

9. **Context Management**: Results are truncated to prevent context overflow, memory has message window limit

10. **Extensibility**: Easy to add new capabilities (regular or composite) without changing core flow

---

## Next Steps

To understand the system better:

1. **Read the code** in this order:
   - `PlannerResponse.java` - Understand response structure
   - `PlannerAgent.java` - See how planning works
   - `PlannerController.java` - See how requests are handled
   - `MathematicsCapability.java` - See composite capability in action
   - `MethodFinderAgent.java` - See sub-agent logic

2. **Run the system** and watch the logs to see the flow in action

3. **Try different requests**:
   - Simple: "Calculate 10 + 20"
   - Multi-step: "Fetch data, analyze it, and generate a report"
   - Clarification: "Generate a report" (planner will ask which data)

4. **Create your own capability**:
   - Regular: Extend `Capability` class
   - Composite: Extend `CompositeCapability` class

---

**End of Document**


