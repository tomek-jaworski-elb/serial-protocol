window.onpageshow = function () {
    // Set canvases dimensions to match the container
    const container = document.querySelector('.canvas-container');
    for (let elementsByTagNameElement of container.getElementsByTagName('canvas')) {
        if (navigator.userAgent.includes('iPhone') || navigator.userAgent.includes('iPad') || navigator.userAgent.includes('ios')) {
            // Set canvas dimensions based on device pixel ratio
            const dpr = window.devicePixelRatio || 1;
            const rect = elementsByTagNameElement.getBoundingClientRect();
            elementsByTagNameElement.width = rect.width * dpr;
            elementsByTagNameElement.height = rect.height * dpr;
            elementsByTagNameElement.style.width = window.innerWidth + 'px';
            elementsByTagNameElement.style.height = window.innerHeight + 'px';
        } else {
            elementsByTagNameElement.width = container.clientWidth;
            elementsByTagNameElement.height = container.clientHeight;
        }
    }

    const imgLedOn = new Image();
    imgLedOn.src = "/img/led_connection_green.bmp";
    const imgLedOff = new Image();
    imgLedOff.src = "/img/led_connection_1.bmp";

    // Ensure images are fully loaded before using them
    const imagesLoaded = Promise.all([
        new Promise(resolve => imgLedOn.onload = resolve),
        new Promise(resolve => imgLedOff.onload = resolve)
    ]);

    let rs_model1_no = 0;
    let rs_model2_no = 0;
    let rs_model3_no = 0;
    let rs_model4_no = 0;
    let rs_model5_no = 0;
    let rs_model6_no = 0;
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
            const newPoints = getScaledPoints(positionX, positionY);
            positionX = newPoints.x;
            positionY = newPoints.y;
            const blinkDuration = 250;
            let canvasName;
            const no_max = 999;
            switch (modelId) {
                case 1:
                    canvasName = "overlayCanvas1"
                    clearCanvas(canvasName);
                    fillFieldValues("heading1", angle);
                    fillFieldValues("speed1", speed);
                    ledBlink('led1', blinkDuration);
                    rs_model1_no++;
                    if (rs_model1_no > no_max) {
                      rs_model1_no = 0;
                    }
                    fillFieldValues0("rs_model1_no", rs_model1_no);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'orange', 12.21, 2, 0);                        // Warta
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 2:
                    canvasName = "overlayCanvas2"
                    clearCanvas(canvasName);
                    fillFieldValues("heading2", angle);
                    fillFieldValues("speed2", speed);
                    ledBlink('led2', blinkDuration);
                    rs_model2_no++;
                    if (rs_model2_no > no_max) {
                      rs_model2_no = 0;
                    }
                    fillFieldValues0("rs_model2_no", rs_model2_no);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'blue', 13.78, 2.38, 0);                       // B.L.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 3:
                    canvasName = "overlayCanvas3"
                    clearCanvas(canvasName);
                    fillFieldValues("heading3", angle);
                    fillFieldValues("speed3", speed);
                    ledBlink('led3', blinkDuration);
                    rs_model3_no++;
                    if (rs_model3_no > no_max) {
                      rs_model3_no = 0;
                    }
                    fillFieldValues0("rs_model3_no", rs_model3_no);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'green', 11.55, 1.8, 0);                       // D.L.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 4:
                    canvasName = "overlayCanvas4"
                    clearCanvas(canvasName);
                    fillFieldValues("heading4", angle);
                    fillFieldValues("speed4", speed);
                    ledBlink('led4', blinkDuration);
                    rs_model4_no++;
                    if (rs_model4_no > no_max) {
                      rs_model4_no = 0;
                    }
                    fillFieldValues0("rs_model4_no", rs_model4_no);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'purple', 15.5, 1.79, 0);                      // Ch.L.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 5:
                    canvasName = "overlayCanvas5"
                    clearCanvas(canvasName);
                    fillFieldValues("heading5", angle);
                    fillFieldValues("speed5", speed);
                    ledBlink('led5', blinkDuration);
                    rs_model5_no++;
                    if (rs_model5_no > no_max) {
                      rs_model5_no = 0;
                    }
                    fillFieldValues0("rs_model5_no", rs_model5_no);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'white', 10.98, 1.78, 1);                      // PROM
                    //                                        "Position_GPS" = Length / 2 + PositionGPS * Length / 10
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 6:
                    canvasName = "overlayCanvas6"
                    clearCanvas(canvasName);
                    fillFieldValues("heading6", angle);
                    fillFieldValues("speed6", speed);
                    ledBlink('led6', blinkDuration);
                    rs_model6_no++;
                    if (rs_model6_no > no_max) {
                      rs_model6_no = 0;
                    }
                    fillFieldValues0("rs_model6_no", rs_model6_no);
                    drawShip(canvasName, positionX, positionY, 3, angle, 'Blue', 16.43, 2.23, 0);                       // L.M.
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                default:
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

    // Function to clear the first canvas
    function clearCanvas(elementId) {
        const element = document.getElementById(elementId);
        const context = element.getContext('2d');
        context.clearRect(0, 0, element.width, element.height);
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
    function fillFieldValues0(elementId, value) {
        const spanElement = document.getElementById(elementId);
        spanElement.innerHTML = value;
    }

    function ledBlink(elementId, duration) {
        imagesLoaded.then(() => {
            const element = document.getElementById(elementId);
            if (!element) {
                console.error(`Element with ID '${elementId}' not found`);
                return;
            }
            element.src = imgLedOn.src;
            setTimeout(() => {
                element.src = imgLedOff.src;
            }, duration);
        }).catch(error => {
            console.error('Error loading images:', error);
        });
    }
};
