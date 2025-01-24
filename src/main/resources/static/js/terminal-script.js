    const rs = "/rs";
    const json = "/json";
    window.onload = function() {
    connectWebSocket(rs, 'rsMessages');
        connectWebSocket(json, 'testMessages');
    }

    function connectWebSocket(endpoint, messageAreaId) {
        const ws = new WebSocket(`${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.hostname}:${window.location.port}${endpoint}`);

        ws.onopen = function(event) {
            console.log(`Connected to ${endpoint}`);
<!--            appendMessage(messageAreaId, `Connected to ${endpoint}`);-->
        };

        ws.onmessage = function(event) {
            console.log(`Message from ${endpoint}: `, event.data);
                    const textArea = document.getElementById("rawData");
                    const textContent = event.data;
                    console.log("Endpoint: " + endpoint)

        if (endpoint == rs) {
        console.log("Yes RS")
        appendMessage("parseData", textContent);
        } else {
        console.log("Yes JSON")
        appendMessage("rawData", textContent);
        }

        };

        ws.onclose = function(event) {
            console.log(`Disconnected from ${endpoint}`);
<!--            appendMessage(messageAreaId, `Disconnected from ${endpoint}`);-->
        };

        ws.onerror = function(event) {
            console.error(`Error in ${endpoint}: `, event);
<!--            appendMessage(messageAreaId, `Error: ${event}`);-->
        };
    }

    function appendMessage(areaId, message) {
        const textMessage = document.getElementById(areaId);
                        const val = textMessage.value.trim();
        if (val === "") {
        textMessage.value = message;
        } else {
        textMessage.value += "\n" + message;
        }
    }