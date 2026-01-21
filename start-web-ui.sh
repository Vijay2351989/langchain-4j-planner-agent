#!/bin/bash

# Planner Agent Web UI Startup Script

echo "=========================================="
echo "  Planner Agent Web UI"
echo "=========================================="
echo ""

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "❌ ERROR: OPENAI_API_KEY environment variable is not set!"
    echo ""
    echo "Please set it with:"
    echo "  export OPENAI_API_KEY=your-api-key"
    echo ""
    exit 1
fi

echo "✓ OPENAI_API_KEY is set"
echo ""

# Check if gradle is available
if ! command -v gradle &> /dev/null; then
    echo "❌ ERROR: Gradle is not installed or not in PATH"
    exit 1
fi

echo "✓ Gradle found"
echo ""

# Build the project
echo "Building project..."
gradle compileJava --console=plain

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo ""
echo "✓ Build successful"
echo ""

# Start the web server
echo "Starting Planner Agent Web UI..."
echo ""
echo "=========================================="
echo "  Server will start on:"
echo "  http://localhost:8080"
echo "=========================================="
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

gradle runPlannerWeb --console=plain

