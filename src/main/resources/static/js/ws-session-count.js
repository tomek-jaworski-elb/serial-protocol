const pathSession = '/session';
let socketSessions;

function createWebSocket() {
    const ws = new WebSocket(`${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.hostname}:${window.location.port}${pathSession}`);

    ws.onmessage = function (event) {
        console.log("WebSocket message received: ", event.data);
        const messagesContainer = document.getElementById('sessions');
        if (messagesContainer) {
            messagesContainer.innerHTML = event.data;
        }
    };

    ws.onerror = function (error) {
        console.error("WebSocket error: ", error);
    };

    ws.onopen = function () {
        console.log("WebSocket connection opened.");
    };

    ws.onclose = function () {
        console.log("WebSocket connection closed.");
    };

    return ws;
}

socketSessions = createWebSocket();

document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
        if (socket) {
            socket.close();
        }
    } else {
        if (!socket || socket.readyState === WebSocket.CLOSED) {
            socket = createWebSocket();
        }
    }
});