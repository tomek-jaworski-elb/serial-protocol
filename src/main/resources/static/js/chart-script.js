window.onpageshow = function () {
    // Set canvases dimensions to match the container
    const container = document.querySelector('.canvas-container');
    for (let elementsByTagNameElement of container.getElementsByTagName('canvas')) {
        if (navigator.userAgent.includes('iPhone') || navigator.userAgent.includes('iPad')) {
            elementsByTagNameElement.width = 4000;
            elementsByTagNameElement.height = 6000;
        } else {
            elementsByTagNameElement.width = container.clientWidth;
            elementsByTagNameElement.height = container.clientHeight;
        }
    }

    // Websocket configuration
    const path = '/json';
// Create a WebSocket instance
    const socket = new WebSocket(`ws://${hostname}:${port}${path}`);
    // Set up the text field
    const textField = document.getElementById("textField");

    socket.onmessage = function (event) {
        console.log("WebSocket message received: ", event.data);

        // Update the text field
        textField.textContent = event.data;
        try {
            const data = JSON.parse(event.data)
            const modelId = Number(data.modelName);
            let positionX = parseFloat(data.positionX);
            let positionY = parseFloat(data.positionY);
            const angle = parseFloat(data.heading);
            const speed = parseFloat(data.speed);
            const scaleX = 1;
            const scaleY = 1;
            const shiftX = 0;
            const shiftY = 0;
            const newPoints = getScaledPoints(positionX, positionY);
            positionX = newPoints.x;
            positionY = newPoints.y;
            const blinkDuration = 70;
            let canvasName;
            switch (modelId) {
                case 1:
                    canvasName = "overlayCanvas1"
                    clearCanvas(canvasName);
                    fillFieldValues("heading1", angle);
                    fillFieldValues("speed1", speed);
                    lightenBackgroundColor("bg-color-1", "yellow", blinkDuration);
                    //drawShip(canvasName, positionX, positionY, 8, angle, 'orange', Length, Beam, PositionGPS);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'orange', 12.21, 2, 0);                        // Warta
                    //drawTriangle(canvasName, positionX, positionY, 8, angle, 'orange');                               // Kalibracja DanePM do MAPY
//                  drawTriangle(canvasName, (  0    + 60+4) * 3.61 , ( 0    + 506) * 3.61 , 6,    1, 'white');         // pozycja 0 x 0             0x0
//                  drawTriangle(canvasName, ( 77.07 + 60+4) * 3.61 , (97.25 + 506) * 3.61 , 6,    1, 'orange');        // SBM    -97.25x77.07
//                  drawTriangle(canvasName, (378.3  + 60+4) * 3.61 , (191.8 + 506) * 3.61 , 6,    1, 'orange');        // FPSO   -191.8x378.3
//                  drawTriangle(canvasName, (-25  + 64) * 3.61 , (   84 + 506) * 3.61 , 6,    1, 'red');               // <- nabieznik             -84x25
//                  drawTriangle(canvasName, ( 82.8+ 64) * 3.61 , (  -69 + 506) * 3.61 , 6,    1, 'red');               // port nabieznik ->         69x82.8
//                  drawTriangle(canvasName, (  2  + 64) * 3.61 , ( -130 + 506) * 3.61 , 6,    1, 'red');               // pomost Lesniczowka        130x2
//                  drawTriangle(canvasName, ( 79  + 64) * 3.61 , ( -188 + 506) * 3.61 , 6,    1, 'red');               // Slip kolej END           188x79
//                  drawTriangle(canvasName, (570  + 64) * 3.61 , ( -362 + 506) * 3.61 , 6,    1, 'red');               // boja kompielisko         320x570
//                  drawTriangle(canvasName, (820  + 64) * 3.61 , (  610 + 506) * 3.61 , 6,    1, 'red');               // -> zatoka               -610x820
//                  drawTriangle(canvasName, (926  + 64) * 3.61 , ( 1149 + 506) * 3.61 , 6,    1, 'red');               // Wiata END jeziora      -1149x926
/*
Linux -> Futro 720 :
serial-ports-server  | 18-07-2024 14:11:06.876 [scheduling-1   ] INFO  c.j.s.s.utils.impl.SerialPortChecker.checkPorts - Checking all open ports health...
serial-ports-server  | 18-07-2024 14:11:06.940 [main           ] INFO  c.j.s.s.controller.SerialController.lambda$openAllPorts$1 - Found 1 ports: (Physical Port S0)
serial-ports-server  | 18-07-2024 14:11:06.953 [main           ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
serial-ports-server  | 18-07-2024 14:11:06.960 [main           ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Physical Port S0 with baud rate 9600 added listener: true

Windows10 -> PC_FBZ :
18-07-2024 16:14:13.052 [scheduling-1   ] INFO  c.j.s.s.utils.impl.SerialPortChecker.checkPorts - Checking all open ports health...
18-07-2024 16:14:13.113 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.lambda$openAllPorts$1 - Found 11 ports: (Multifunction Device,Multifunction Device,Multifunction Device,Multifunction Device,Multifunction Device,Multifunction Device,Port komunikacyjny (COM1),com0com - serial port emulator,com0com - serial port emulator,com0com - serial port emulator,com0com - serial port emulator)
18-07-2024 16:14:13.151 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.153 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Multifunction Device with baud rate 9600 added listener: true
18-07-2024 16:14:13.155 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.156 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Multifunction Device with baud rate 9600 added listener: true
18-07-2024 16:14:13.159 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.159 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Multifunction Device with baud rate 9600 added listener: true
18-07-2024 16:14:13.163 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.163 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Multifunction Device with baud rate 9600 added listener: true
18-07-2024 16:14:13.166 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.167 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Multifunction Device with baud rate 9600 added listener: true
18-07-2024 16:14:13.171 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.171 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Multifunction Device with baud rate 9600 added listener: true
18-07-2024 16:14:13.173 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.174 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port Port komunikacyjny (COM1) with baud rate 9600 added listener: true
18-07-2024 16:14:13.176 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.176 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port com0com - serial port emulator with baud rate 9600 added listener: true
18-07-2024 16:14:13.178 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.178 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port com0com - serial port emulator with baud rate 9600 added listener: true
18-07-2024 16:14:13.181 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.181 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port com0com - serial port emulator with baud rate 9600 added listener: true
18-07-2024 16:14:13.186 [restartedMain  ] INFO  c.j.s.s.l.SerialPortListenerImpl.getMessageDelimiter - Set message delimiter: [13, 10]
18-07-2024 16:14:13.186 [restartedMain  ] INFO  c.j.s.s.controller.SerialController.openAllPorts - On port com0com - serial port emulator with baud rate 9600 added listener: true
18-07-2024 16:14:13.197 [Thread-14      ] INFO  c.j.s.s.l.SerialPortListenerImpl.serialEvent - On port Port komunikacyjny (COM1) Received delimited message: [16, -120, -115, 108, -12, -74, -126, 19, -119, -56, 37, 65, 111, 0, 4, -83, 13, 10]
*/

                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 2:
                    canvasName = "overlayCanvas2"
                    clearCanvas(canvasName);
                    fillFieldValues("heading2", angle);
                    fillFieldValues("speed2", speed);
                    lightenBackgroundColor("bg-color-2", "lightblue", blinkDuration);
                    //drawShip(canvasName, positionX, positionY, 8, angle, 'orange', Length, Beam, PositionGPS);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'blue', 13.78, 2.38, 0);                       // B.L.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 3:
                    canvasName = "overlayCanvas3"
                    clearCanvas(canvasName);
                    fillFieldValues("heading3", angle);
                    fillFieldValues("speed3", speed);
                    lightenBackgroundColor("bg-color-3", "lightgreen", blinkDuration);
                    //drawShip(canvasName, positionX, positionY, 8, angle, 'orange', Length, Beam, PositionGPS);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'green', 11.55, 1.8, 0);                       // D.L.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 4:
                    canvasName = "overlayCanvas4"
                    clearCanvas(canvasName);
                    fillFieldValues("heading4", angle);
                    fillFieldValues("speed4", speed);
                    lightenBackgroundColor("bg-color-4", "darkmagenta", blinkDuration);
                    //drawShip(canvasName, positionX, positionY, 8, angle, 'orange', Length, Beam, PositionGPS);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'purple', 15.5, 1.79, 0);                      // Ch.L.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 5:
                    canvasName = "overlayCanvas5"
                    clearCanvas(canvasName);
                    fillFieldValues("heading5", angle);
                    fillFieldValues("speed5", speed);
                    lightenBackgroundColor("bg-color-5", "lightgrey", blinkDuration);
                    //drawShip(canvasName, positionX, positionY, 8, angle, 'orange', Length, Beam, PositionGPS);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'white', 10.98, 1.78, 1);                      // PROM
                    //                                        "Position_GPS" = Length / 2 + PositionGPS * Length / 10
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 6:
                    canvasName = "overlayCanvas6"
                    clearCanvas(canvasName);
                    fillFieldValues("heading6", angle);
                    fillFieldValues("speed6", speed);
                    lightenBackgroundColor("bg-color-6", "mediumblue", blinkDuration);
                    //drawShip(canvasName, positionX, positionY, 8, angle, 'orange', Length, Beam, PositionGPS);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'Blue', 16.43, 2.23, 0);                       // L.M.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                default:
//                    clearCanvas("overlayCanvas4");
//                    drawTriangle('overlayCanvas4', positionX, positionY, 12, angle, 'black');
                    console.log("Unknown model ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
            }
        } catch (error) {
            console.error("Error parsing JSON data:", error);
        }
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

    function getScaledPoints(oldX, oldY) {
        const bgY = backgroundCanvas.height;                                                                            // 4000x6000
        const bgX = backgroundCanvas.width;
        const staticShift_y = 506;                                                                                      // 506
        const staticShift_x = 64;                                                                                       //  64
        scaleX = 3.61;                                                                                                  //   3.61
        scaleY = 3.61;                                                                                                  //   3.61
        console.log("ScaleX: " + scaleX + ", ScaleY: " + scaleY);
        console.log("Old X: " + oldX + ", Old Y:  " + oldY)
        // Changed coordinate system x->y , y->x
        const y = (-oldX + staticShift_y) * scaleY;
        const x = (oldY + staticShift_x ) * scaleX;
        console.log("New X: " + x + ", New Y: " +  y);
        return {x, y};
    }

    // Function to generate a random point within the canvas
    function getRandomPoint(elementId) {
        const element = document.getElementById(elementId);
        const x = Math.random() * element.width;
        const y = Math.random() * element.height;
        return {x, y};
    }

    function getRandomAngle() {
        return Math.random() * 2 * 180;
    }

    // Function to clear the first canvas
    function clearCanvas(elementId) {
        const element = document.getElementById(elementId);
        const context = element.getContext('2d');
        context.clearRect(0, 0, element.width, element.height);
    }

// Function to draw a triangle
    function drawTriangle(elementId, x, y, scale, angle, fillColor) {
        const element = document.getElementById(elementId);
        const ctx = element.getContext('2d');
        // Define the vertices of the triangle (equilateral triangle centered at origin)
        let vertices = [
            {x: 0, y: -1},
            {x: 0.866, y: 0.5},
            {x: -0.866, y: 0.5}
        ];

        // Scale the vertices
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x * scale,
                y: vertex.y * scale
            };
        });

        // Rotate the vertices
        const radians = angle * Math.PI / 180;
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x * Math.cos(radians) - vertex.y * Math.sin(radians),
                y: vertex.x * Math.sin(radians) + vertex.y * Math.cos(radians)
            };
        });

        // Translate the vertices to the (x, y) position
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x + x,
                y: vertex.y + y
            };
        });

        // Draw the triangle
        ctx.beginPath();
        ctx.moveTo(vertices[0].x, vertices[0].y);
        for (let i = 1; i < vertices.length; i++) {
            ctx.lineTo(vertices[i].x, vertices[i].y);
        }
        ctx.closePath();

        // Fill and stroke
        ctx.fillStyle = fillColor;
        ctx.fill();
        ctx.strokeStyle = 'black';
        ctx.lineWidth = 1;
        ctx.stroke();
    }

// Function to draw a Ship
    function drawShip(elementId, x, y, scale, angle, fillColor, yy, xx, pp) {
        const element = document.getElementById(elementId);
        const ctx = element.getContext('2d');
        // Define the outline of the ship (ship with the bow at the beginning)
        let vertices = [
            {x:       0, y: -0.5*yy + pp*(yy/10)},                                                                      // nr1    //      1      |
            {x:  0.5*xx, y: -0.4*yy + pp*(yy/10)},                                                                      // nr2    //   2     5
            {x:  0.5*xx, y:  0.5*yy + pp*(yy/10)},                                                                      // nr3    //      o      y
            {x: -0.5*xx, y:  0.5*yy + pp*(yy/10)},                                                                      // nr4    //
            {x: -0.5*xx, y: -0.4*yy + pp*(yy/10)}                                                                       // nr5    //   3     4   |
        ];                                                                                                              //             -  x  -

        // Scale the vertices
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x * scale * 1.1 ,                                                                             //  * 1.1
                y: vertex.y * scale
            };
        });

        // Rotate the vertices
        const radians = angle * Math.PI / 180;
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x * Math.cos(radians) - vertex.y * Math.sin(radians),
                y: vertex.x * Math.sin(radians) + vertex.y * Math.cos(radians)
            };
        });

        // Translate the vertices to the (x, y) position
        vertices = vertices.map(vertex => {
            return {
                x: vertex.x + x,
                y: vertex.y + y
            };
        });

        // Draw the ship
        ctx.beginPath();
        ctx.moveTo(vertices[0].x, vertices[0].y);
        for (let i = 1; i < vertices.length; i++) {
            ctx.lineTo(vertices[i].x, vertices[i].y);
        }
        ctx.closePath();

        // Fill and stroke
        ctx.fillStyle = fillColor;
        ctx.fill();
        ctx.strokeStyle = 'black';
        ctx.lineWidth = 1;
        ctx.stroke();
    }

    function fillFieldValues(elementId, value) {
        const spanElement = document.getElementById(elementId);
        spanElement.innerHTML = value.toFixed(1);
    }

    function lightenBackgroundColor(elementId, lighterColor, duration) {
        const element = document.getElementById(elementId);
        const originalBackgroundColor = window.getComputedStyle(element).backgroundColor;
        element.style.backgroundColor = lighterColor;
        setTimeout(() => {
            element.style.backgroundColor = originalBackgroundColor;
        }, duration);
    }
};
