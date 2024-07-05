const hostname = window.location.hostname; // Gets the hostname of the current page
const port = 8081;
const path = '/heartbeat';
// Create a WebSocket instance
const socket = new WebSocket(`ws://${hostname}:${port}${path}`);

socket.onmessage = function (event) {
    console.log("WebSocket message received: ", event.data);
    const messagesContainer = document.getElementById('messages');
    const message = document.createElement('div');
    message.textContent = event.data;
    messagesContainer.innerHTML = event.data;
};

socket.onerror = function (error) {
    console.error("WebSocket error: ", error);
};

socket.onopen = function (event) {
    console.log("WebSocket connection opened.");
};

socket.onclose = function (event) {
    console.log("WebSocket connection closed.");
};