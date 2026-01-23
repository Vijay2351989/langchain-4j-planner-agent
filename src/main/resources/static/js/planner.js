// Planner Agent Web UI JavaScript

let stompClient = null;
let sessionId = generateSessionId();
let currentSubscription = null;  // Track current subscription
let isProcessing = false;
let taskCompleted = false;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    connect();
    setupEventListeners();
    updateSessionDisplay();
});

function generateSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);
        updateStatus('Connected', 'success');

        // Subscribe to session-specific topic
        subscribeToSession();
    }, function(error) {
        console.error('Connection error:', error);
        updateStatus('Disconnected', 'error');
    });
}

function subscribeToSession() {
    // Unsubscribe from previous session if exists
    if (currentSubscription) {
        console.log('Unsubscribing from previous session');
        currentSubscription.unsubscribe();
    }

    // Subscribe to session-specific topic to avoid receiving messages from other sessions
    const sessionTopic = '/topic/response/' + sessionId;
    console.log('Subscribing to: ' + sessionTopic);
    currentSubscription = stompClient.subscribe(sessionTopic, function(message) {
        handleResponse(JSON.parse(message.body));
    });
}

function setupEventListeners() {
    document.getElementById('sendBtn').addEventListener('click', sendRequest);
    document.getElementById('clearBtn').addEventListener('click', clearChat);
    document.getElementById('newSessionBtn').addEventListener('click', startNewSession);
    document.getElementById('addVariableBtn').addEventListener('click', addVariable);
    document.getElementById('selectAllBtn').addEventListener('click', selectAllCapabilities);
    document.getElementById('deselectAllBtn').addEventListener('click', deselectAllCapabilities);
    document.getElementById('userInput').addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendRequest();
        }
    });
}

function sendRequest() {
    const input = document.getElementById('userInput');
    const request = input.value.trim();

    if (!request || isProcessing) return;

    // If task was completed, start a new session automatically
    if (taskCompleted) {
        startNewSession();
    }

    // Collect selected capabilities
    const selectedCapabilities = collectSelectedCapabilities();

    if (selectedCapabilities.length === 0) {
        alert('Please select at least one capability!');
        return;
    }

    // Collect input variables
    const inputVariables = collectInputVariables();

    // Display request with variables and capabilities
    let displayMessage = request;

    if (selectedCapabilities.length > 0) {
        displayMessage += '<br><br><strong>Selected Capabilities:</strong> ';
        displayMessage += selectedCapabilities.join(', ');
    }

    if (inputVariables.length > 0) {
        displayMessage += '<br><br><strong>Input Variables:</strong><br>';
        inputVariables.forEach(v => {
            const typeLabel = v.type ? ` <em>(${v.type})</em>` : '';
            let displayValue = v.value;

            // Truncate long values for display
            if (displayValue.length > 100) {
                displayValue = displayValue.substring(0, 100) + '...';
            }

            // Format JSON for better display
            if (v.type === 'json') {
                try {
                    const parsed = JSON.parse(v.value);
                    displayValue = '<code>' + JSON.stringify(parsed, null, 2).substring(0, 200) + '</code>';
                    if (v.value.length > 200) displayValue += '...';
                } catch (e) {
                    // Keep original value if JSON parsing fails
                }
            }

            displayMessage += `• <strong>${v.name}</strong>${typeLabel}: ${displayValue}<br>`;
        });
    }

    addMessage('user', 'You', displayMessage);
    input.value = '';

    isProcessing = true;
    taskCompleted = false;
    updateStatus('Planning...', 'processing');

    stompClient.send('/app/plan', {}, JSON.stringify({
        sessionId: sessionId,
        request: request,
        inputVariables: inputVariables,
        selectedCapabilities: selectedCapabilities
    }));
}

function handleResponse(response) {
    console.log('Received response:', response);
    
    if (response.type === 'error') {
        addMessage('error', 'Error', response.message);
        isProcessing = false;
        updateStatus('Error', 'error');
        return;
    }
    
    if (response.type === 'planner_response') {
        handlePlannerResponse(response.data);
    } else if (response.type === 'execution_result') {
        handleExecutionResult(response.data);
    } else if (response.type === 'reset') {
        addMessage('system', 'System', response.message);
        isProcessing = false;
        updateStatus('Ready', 'success');
    }
}

function handlePlannerResponse(data) {
    const responseType = getResponseType(data);

    let message = `<strong>Type:</strong> ${responseType}<br>`;
    message += `<strong>ID:</strong> ${data.id}<br>`;
    message += `<strong>Name:</strong> ${data.name}<br>`;
    message += `<strong>Description:</strong> ${data.description}`;

    if (data.input) {
        // Convert input to string for display (handle both string and object)
        let inputStr = typeof data.input === 'object'
            ? JSON.stringify(data.input, null, 2)
            : data.input;
        message += `<br><strong>Input:</strong> ${truncate(inputStr, 100)}`;
    }

    addMessage('planner', 'Planner', message);

    if (data.id > 0) {
        // Execute capability
        updateStatus('Executing ' + data.name + '...', 'processing');
        executeCapability(data.id, data.input);
    } else if (data.id === -2) {
        // Complete - usage report is automatically generated on the server
        addMessage('complete', 'Complete',
            '✓ Task completed successfully!<br>' +
            '📊 Usage report generated: <code>usage-reports/' + sessionId + '.xlsx</code><br>' +
            '💡 You can start a new task or click "New Session" to begin fresh.');
        isProcessing = false;
        taskCompleted = true;
        updateStatus('Complete - Usage report saved', 'success');
    } else if (data.id === 0) {
        // Clarification needed
        isProcessing = false;
        updateStatus('Clarification needed', 'warning');
    } else {
        // Unable to identify
        isProcessing = false;
        updateStatus('Unable to proceed', 'error');
    }
}

function handleExecutionResult(data) {
    const status = data.success ? '✓' : '✗';
    const message = `${status} ${data.message}<br><strong>Output:</strong> ${truncate(data.output, 150)}`;
    
    addMessage('execution', 'Execution Result', message);
}

function executeCapability(capabilityId, input) {
    stompClient.send('/app/execute', {}, JSON.stringify({
        sessionId: sessionId,
        capabilityId: capabilityId,
        input: input
    }));
}

function getResponseType(data) {
    if (data.id > 0) return 'CAPABILITY';
    if (data.id === 0) return 'CLARIFICATION';
    if (data.id === -2) return 'COMPLETE';
    return 'UNABLE';
}

function addMessage(type, sender, content) {
    const chatLog = document.getElementById('chatLog');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;
    
    const timestamp = new Date().toLocaleTimeString();
    
    messageDiv.innerHTML = `
        <div class="message-header">${sender}</div>
        <div class="message-content">${content}</div>
        <div class="message-meta">${timestamp}</div>
    `;
    
    chatLog.appendChild(messageDiv);
    chatLog.scrollTop = chatLog.scrollHeight;
}

function clearChat() {
    // Save the old session ID before generating a new one
    const oldSessionId = sessionId;

    // Clear UI
    document.getElementById('chatLog').innerHTML = '';
    clearVariables();
    taskCompleted = false;

    // Generate new session ID
    sessionId = generateSessionId();
    updateSessionDisplay();

    // Clear the OLD session on the server (this generates the usage report)
    stompClient.send('/app/reset', {}, JSON.stringify({
        sessionId: oldSessionId
    }));
}

function startNewSession() {
    const oldSessionId = sessionId;
    sessionId = generateSessionId();
    taskCompleted = false;

    addMessage('system', 'System', `🔄 New session started. Previous session: ${oldSessionId.substring(0, 20)}...`);
    updateSessionDisplay();
    updateStatus('New session - Ready', 'success');

    // Resubscribe to new session topic
    subscribeToSession();

    // Clear the old session on the server
    stompClient.send('/app/reset', {}, JSON.stringify({
        sessionId: oldSessionId
    }));
}

function updateStatus(text, type) {
    const statusText = document.getElementById('statusText');
    statusText.textContent = text;
    
    // Update button state
    const sendBtn = document.getElementById('sendBtn');
    sendBtn.disabled = isProcessing;
}

function updateSessionDisplay() {
    document.getElementById('sessionId').textContent = `Session: ${sessionId}`;
}

function truncate(str, maxLength) {
    if (!str) return 'null';
    if (str.length <= maxLength) return str;
    return str.substring(0, maxLength) + '...';
}

// Input Variables Management
function addVariable() {
    const variablesList = document.getElementById('variablesList');

    const variableItem = document.createElement('div');
    variableItem.className = 'variable-item';

    const varId = 'var-' + Date.now();

    variableItem.innerHTML = `
        <input type="text" class="var-name" placeholder="Variable name (e.g., user_id)" />
        <select class="var-type" onchange="handleVariableTypeChange(this)">
            <option value="text">Text</option>
            <option value="json">JSON</option>
            <option value="file">File</option>
        </select>
        <textarea class="var-value var-value-text" placeholder="Variable value (e.g., 12345)" rows="1"></textarea>
        <input type="file" class="var-value-file" id="${varId}" style="display: none;" onchange="handleFileSelect(this)" />
        <button class="btn-remove" onclick="removeVariable(this)">Remove</button>
    `;

    variablesList.appendChild(variableItem);
}

function handleVariableTypeChange(selectElement) {
    const variableItem = selectElement.parentElement;
    const type = selectElement.value;
    const textArea = variableItem.querySelector('.var-value-text');
    const fileInput = variableItem.querySelector('.var-value-file');

    if (type === 'file') {
        textArea.style.display = 'none';
        fileInput.style.display = 'block';
        textArea.value = ''; // Clear text value
    } else {
        textArea.style.display = 'block';
        fileInput.style.display = 'none';
        fileInput.value = ''; // Clear file selection

        if (type === 'json') {
            textArea.placeholder = 'Enter JSON (e.g., {"key": "value"})';
            textArea.rows = 3;
        } else {
            textArea.placeholder = 'Variable value (e.g., 12345)';
            textArea.rows = 1;
        }
    }
}

function handleFileSelect(fileInput) {
    const file = fileInput.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = function(e) {
            // Store file content in a data attribute
            fileInput.setAttribute('data-file-content', e.target.result);
            fileInput.setAttribute('data-file-name', file.name);
        };
        reader.readAsText(file);
    }
}

function removeVariable(button) {
    button.parentElement.remove();
}

function collectInputVariables() {
    const variables = [];
    const variableItems = document.querySelectorAll('.variable-item');

    variableItems.forEach(item => {
        const name = item.querySelector('.var-name').value.trim();
        const type = item.querySelector('.var-type').value;
        let value = '';

        if (type === 'file') {
            const fileInput = item.querySelector('.var-value-file');
            const fileContent = fileInput.getAttribute('data-file-content');
            const fileName = fileInput.getAttribute('data-file-name');

            if (fileContent) {
                value = fileContent;
                // Optionally include filename in the variable
                // value = JSON.stringify({ filename: fileName, content: fileContent });
            }
        } else {
            value = item.querySelector('.var-value-text').value.trim();

            // Validate JSON if type is json
            if (type === 'json' && value) {
                try {
                    JSON.parse(value); // Validate JSON
                } catch (e) {
                    addMessage('system', `Warning: Variable "${name}" has invalid JSON format`);
                }
            }
        }

        if (name && value) {
            variables.push({ name, value, type });
        }
    });

    return variables;
}

function clearVariables() {
    document.getElementById('variablesList').innerHTML = '';
}

// Capability Selection Management
function collectSelectedCapabilities() {
    const capabilities = [];
    const checkboxes = document.querySelectorAll('.cap-checkbox:checked');

    checkboxes.forEach(checkbox => {
        capabilities.push(parseInt(checkbox.value));
    });

    return capabilities;
}

function selectAllCapabilities() {
    const checkboxes = document.querySelectorAll('.cap-checkbox');
    checkboxes.forEach(checkbox => {
        checkbox.checked = true;
    });
}

function deselectAllCapabilities() {
    const checkboxes = document.querySelectorAll('.cap-checkbox');
    checkboxes.forEach(checkbox => {
        checkbox.checked = false;
    });
}

