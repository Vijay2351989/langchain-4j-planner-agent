# Planner Agent Documentation

Welcome to the Planner Agent documentation! This directory contains comprehensive guides to help you understand and work with the Planner Agent system.

## 📚 Documentation Files

### 🎨 [diagrams/](diagrams/) - **Interactive Visual Diagrams**
**HTML diagrams that can be opened in browser and exported as PNG/SVG**

Open `diagrams/index.html` in your browser to access:
- **System Architecture** - Complete component overview with data flow
- **Response Decision Flow** - How different response types are handled
- **Composite Capability Flow** - Two-tier architecture sequence diagram

**Features:**
- Interactive and zoomable
- Export as SVG or print to PDF
- Easy screenshot for PNG export
- Works offline
- No installation required

**Quick access:** Just double-click any `.html` file in the `diagrams/` folder!

---

### 1. [EXECUTION_FLOW.md](EXECUTION_FLOW.md) - **START HERE**
**Comprehensive mental model of the entire system**

This is the main documentation file that provides:
- Complete system architecture overview
- Detailed step-by-step execution flows
- Regular capability execution flow
- Composite capability execution flow (Mathematics example)
- Response types and their meanings
- WebSocket communication details
- Session management
- Key files reference
- Visual diagrams

**Best for:** Understanding how everything works together, learning the system architecture, debugging complex issues

**Read this if you want to:**
- Understand the complete request-to-response flow
- Learn how composite capabilities work
- See how the planner makes decisions
- Understand the two-tier architecture
- Debug execution issues

---

### 2. [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
**Quick lookup guide for common tasks**

This is a condensed reference guide with:
- PlannerResponse types table
- WebSocket endpoints
- Key classes and methods
- Capability IDs
- Mathematics methods reference
- Common flows
- Configuration settings
- File locations
- Example requests
- Debugging tips
- Common issues and solutions

**Best for:** Quick lookups, finding specific information, troubleshooting

**Use this when you:**
- Need to quickly find a method signature
- Want to know which capability ID to use
- Need to remember the input format for a math operation
- Are debugging a specific issue
- Want example requests to test

---

## 🎯 How to Use This Documentation

### If you're new to the system:
1. **Start with [EXECUTION_FLOW.md](EXECUTION_FLOW.md)**
   - Read the "System Architecture Overview" section
   - Follow "Flow 1: Regular Capability Execution" with a simple example
   - Then read "Flow 2: Composite Capability Execution" to understand the advanced pattern
   - Review the diagrams at the end

2. **Keep [QUICK_REFERENCE.md](QUICK_REFERENCE.md) handy**
   - Bookmark it for quick lookups
   - Use it when you need specific information

### If you're debugging an issue:
1. Check [QUICK_REFERENCE.md](QUICK_REFERENCE.md) → "Common Issues" section
2. Review [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → relevant flow section
3. Check the logs and compare with the documented flow

### If you're adding a new capability:
1. Review [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "Key Components" → Capabilities section
2. Decide: Regular or Composite?
3. Follow the pattern from existing capabilities
4. Update [QUICK_REFERENCE.md](QUICK_REFERENCE.md) → "Capability IDs" table

### If you're modifying the planner logic:
1. Read [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "PlannerAgent" section
2. Understand the conversation memory and context management
3. Review the "Response Types" section
4. Test with various scenarios

---

## 🔑 Key Concepts

### Two-Tier Architecture
The system uses a unique two-tier architecture for composite capabilities:
- **Tier 1:** Planner selects high-level capability (e.g., "Mathematics")
- **Tier 2:** MethodFinder selects specific method (e.g., "add")

This keeps the planner's context small while allowing unlimited methods per capability.

### Response Types
Every planner response has an `id` that determines the action:
- `id > 0` → Execute capability
- `id = 0` → Need clarification
- `id = -1` → Cannot proceed
- `id = -2` → Task complete

### Session Management
Each browser session gets its own:
- PlannerAgent instance
- Conversation memory
- Selected capabilities

### WebSocket Communication
Real-time bidirectional communication:
- Client → Server: `/app/plan`, `/app/execute`, `/app/clarify`, `/app/reset`
- Server → Client: `/topic/response`

---

## 📊 Visual Diagrams

The documentation includes interactive diagrams in the **[diagrams/](diagrams/)** folder:

1. **System Architecture Diagram** - Shows all components and their relationships
2. **PlannerResponse Decision Flow** - Shows how different response types are handled
3. **Composite Capability Sequence Diagram** - Shows the detailed flow for Mathematics

**How to use:**
- Open `diagrams/index.html` in your browser for a visual gallery
- Each diagram can be exported as PNG or SVG
- Diagrams are also embedded in [EXECUTION_FLOW.md](EXECUTION_FLOW.md)

**Export as PNG:**
- Screenshot: Cmd+Shift+4 (Mac) or Win+Shift+S (Windows)
- Download SVG button on each diagram page
- Print to PDF then convert
- See [diagrams/README.md](diagrams/README.md) for detailed instructions

---

## 🚀 Quick Start

1. **Read the overview:**
   - [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "System Architecture Overview"

2. **Follow a simple example:**
   - [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "Flow 1: Regular Capability Execution"

3. **Try it yourself:**
   ```bash
   ./mvnw spring-boot:run
   ```
   Open: http://localhost:8080/planner.html

4. **Test with examples:**
   - "Calculate 10 + 20"
   - "Fetch sales data for Q4 2024"
   - "What is the square root of 144?"

5. **Keep reference handy:**
   - Bookmark [QUICK_REFERENCE.md](QUICK_REFERENCE.md)

---

## 🎓 Learning Path

### Beginner
1. Read [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "System Architecture Overview"
2. Read [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "Flow 1: Regular Capability Execution"
3. Try simple requests in the UI
4. Review [QUICK_REFERENCE.md](QUICK_REFERENCE.md) → "Example Requests"

### Intermediate
1. Read [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "Flow 2: Composite Capability Execution"
2. Understand the two-tier architecture
3. Review the code files mentioned in the flows
4. Try creating a simple regular capability

### Advanced
1. Study [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "Session Management"
2. Review [EXECUTION_FLOW.md](EXECUTION_FLOW.md) → "WebSocket Communication"
3. Create a composite capability
4. Modify planner prompts and test behavior

---

## 📝 Additional Resources

### Code Examples
- See `src/test/java/com/krista/kme/agent/planner/MathematicsCapabilityTest.java` for usage examples
- Review existing capabilities in `src/main/java/com/krista/kme/agent/planner/capabilities/`

### External Documentation
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Spring WebSocket Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [STOMP Protocol](https://stomp.github.io/)

---

## 🤝 Contributing

When adding new features or capabilities:
1. Update the relevant documentation files
2. Add examples to [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
3. Update diagrams if architecture changes
4. Add test cases

---

## 📞 Need Help?

1. **Check the docs:**
   - [EXECUTION_FLOW.md](EXECUTION_FLOW.md) for detailed flows
   - [QUICK_REFERENCE.md](QUICK_REFERENCE.md) for quick answers

2. **Review the code:**
   - Start with the files mentioned in the documentation
   - Follow the execution flow in the debugger

3. **Check the logs:**
   - All components log extensively
   - Compare log output with documented flows

---

**Happy coding! 🚀**

