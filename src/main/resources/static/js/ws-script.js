const path = '/heartbeat';
// Create a WebSocket instance
const socket = new WebSocket(`${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.hostname}:${window.location.port}${path}`);

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