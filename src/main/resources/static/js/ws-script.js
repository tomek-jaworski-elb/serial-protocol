const hostname = window.location.hostname; // Gets the hostname of the current page
const port = 8080;
const path = '/echo';
// Create a WebSocket instance
const socket = new WebSocket(`ws://${hostname}:${port}${path}`);

// Define event listeners for WebSocket
socket.addEventListener('open', () => {
    console.log('Connected to WebSocket server');
});

socket.addEventListener('message', (event) => {
    const messagesContainer = document.getElementById('messages');
    const message = document.createElement('div');
    message.textContent = event.data;
    messagesContainer.innerHTML = event.data;
});

socket.addEventListener('close', () => {
    console.log('WebSocket connection closed');
});

socket.onerror = function(error) {
    console.error("WebSocket error: ", error);
};

// Function to handle WebSocket connection opened
socket.onopen = function(event) {
    console.log("WebSocket connection opened.");
};

// Handle form submission
document.getElementById('message-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const input = document.getElementById('message-input');
    const message = input.value;
    socket.send(message);
    input.value = '';
});