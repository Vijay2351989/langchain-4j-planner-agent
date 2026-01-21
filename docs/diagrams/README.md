# Planner Agent - Architecture Diagrams

This folder contains interactive HTML diagrams that can be opened in any web browser and exported as PNG/SVG images.

## 📊 Available Diagrams

### 1. System Architecture (`system-architecture.html`)
**Complete system overview showing:**
- Browser (Web UI) with WebSocket client
- Spring Boot Backend components
- Capabilities Registry (6 capabilities)
- Composite Capability framework
- AI Services (OpenAI GPT-4o-mini)
- Complete data flow with numbered steps

**Best for:** Understanding the overall system structure and component relationships

---

### 2. Response Decision Flow (`response-flow.html`)
**Decision flowchart showing:**
- How PlannerResponse types are determined
- Actions taken for each response type:
  - CAPABILITY (id > 0) - Execute capability
  - CLARIFICATION (id = 0) - Request user input
  - UNABLE (id = -1) - Show error
  - COMPLETE (id = -2) - Show completion
- Execution success/failure handling
- Loop back to planner for next step

**Best for:** Understanding how the system handles different response scenarios

---

### 3. Composite Capability Flow (`composite-capability-flow.html`)
**Sequence diagram showing:**
- Complete flow for Mathematics capability
- Two-tier architecture in action:
  - Tier 1: Planner selects "Mathematics"
  - Tier 2: MethodFinder selects "add" method
- Step-by-step execution with actual example: "Calculate sum of 15, 23, 42"
- Method selection and execution
- Result reporting back to planner

**Best for:** Understanding how composite capabilities work and why the two-tier architecture is needed

---

## 🚀 Quick Start

### Option 1: View in Browser
1. Open `index.html` in your web browser
2. Click on any diagram card to view that diagram
3. Each diagram page has export options

### Option 2: Direct Access
Open any diagram directly:
- `system-architecture.html`
- `response-flow.html`
- `composite-capability-flow.html`

---

## 📸 How to Export as PNG

Each diagram page includes multiple export options:

### Method 1: Screenshot (Easiest) ⭐
**Mac:**
```
1. Press Cmd + Shift + 4
2. Select the diagram area
3. Image saved to Desktop
```

**Windows:**
```
1. Press Win + Shift + S (Snipping Tool)
2. Select the diagram area
3. Save the image
```

### Method 2: Download as SVG
```
1. Click "Download as SVG" button on diagram page
2. Open SVG in image editor (Inkscape, Illustrator, etc.)
3. Export as PNG with desired resolution
```

### Method 3: Print to PDF
```
1. Click "Print to PDF" button on diagram page
2. Save as PDF
3. Convert PDF to PNG using:
   - Preview (Mac): File → Export → PNG
   - Online tools: pdf2png.com, etc.
```

### Method 4: Browser DevTools (Chrome)
```
1. Right-click on diagram → Inspect
2. In DevTools, find the <svg> element
3. Right-click on <svg> → Capture node screenshot
4. Image saved to Downloads
```

---

## 🎨 Customization

### Changing Diagram Colors
Edit the HTML file and modify the `style` sections in the Mermaid diagram:

```
style Browser fill:#e1f5ff
style Backend fill:#fff4e1
style AI fill:#f0e1ff
```

### Changing Diagram Size
Modify the Mermaid initialization in the `<script>` section:

```javascript
mermaid.initialize({ 
    startOnLoad: true,
    theme: 'default',
    flowchart: {
        useMaxWidth: false,  // Change to false for fixed width
        htmlLabels: true
    }
});
```

### High-Resolution Export
For high-resolution PNG exports:
1. Download as SVG first
2. Open in vector graphics editor (Inkscape, Illustrator)
3. Export as PNG with custom DPI (300 DPI for print quality)

---

## 📁 File Structure

```
docs/diagrams/
├── index.html                      # Main landing page with all diagrams
├── system-architecture.html        # System architecture diagram
├── response-flow.html              # Response decision flow diagram
├── composite-capability-flow.html  # Composite capability sequence diagram
└── README.md                       # This file
```

---

## 🔧 Technical Details

### Technologies Used
- **Mermaid.js** - Diagram rendering engine
- **HTML5/CSS3** - Page structure and styling
- **JavaScript** - Interactive features and SVG export

### Browser Compatibility
- ✅ Chrome/Edge (Recommended)
- ✅ Firefox
- ✅ Safari
- ✅ Opera

### Offline Usage
All diagrams work offline! The Mermaid.js library is loaded from CDN, but you can download it for offline use:
1. Download Mermaid.js from https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js
2. Save to `docs/diagrams/mermaid.min.js`
3. Update script tag in HTML files: `<script src="mermaid.min.js"></script>`

---

## 📚 Related Documentation

- **[../EXECUTION_FLOW.md](../EXECUTION_FLOW.md)** - Detailed execution flows with code examples
- **[../QUICK_REFERENCE.md](../QUICK_REFERENCE.md)** - Quick reference guide
- **[../README.md](../README.md)** - Documentation index

---

## 💡 Tips

1. **For Presentations:** Use the "Print to PDF" option to create slides
2. **For Documentation:** Screenshot method gives best quality for embedding in docs
3. **For Editing:** Download SVG and edit in vector graphics software
4. **For Sharing:** Share the HTML files directly - they're self-contained
5. **For High-DPI Displays:** SVG export maintains quality at any resolution

---

## 🐛 Troubleshooting

### Diagram Not Rendering
- Wait a few seconds for Mermaid.js to load from CDN
- Check browser console for errors
- Try refreshing the page
- Ensure JavaScript is enabled

### Export Button Not Working
- Make sure diagram has fully rendered
- Try a different browser (Chrome recommended)
- Check browser console for errors

### Low Quality PNG
- Use SVG export instead, then convert at higher DPI
- Or use browser DevTools screenshot method
- Avoid browser zoom when taking screenshots

---

## 📝 Notes

- All diagrams are **interactive** and can be zoomed/panned in some browsers
- Diagrams are **responsive** and adapt to different screen sizes
- Each diagram includes **detailed instructions** on the page
- **No installation required** - just open in a browser!

---

**Need help?** Check the main documentation in `docs/README.md` or `docs/EXECUTION_FLOW.md`

