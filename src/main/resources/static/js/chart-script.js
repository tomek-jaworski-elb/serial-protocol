window.onload = function () {
    // Set up the canvases and their contexts
    const backgroundCanvas = document.getElementById('backgroundCanvas');
    const bgCtx = backgroundCanvas.getContext('2d');

    // Set canvases dimensions to match the container
    const container = document.querySelector('.canvas-container');
    for (let elementsByTagNameElement of container.getElementsByTagName('canvas')) {
        elementsByTagNameElement.width = container.clientWidth;
        elementsByTagNameElement.height = container.clientHeight;
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
            const scaleX = 1;
            const scaleY = 1;
            const shiftX = 0;
            const shiftY = 0;
            const newPoints = getScaledPoints(positionX, positionY);
            positionX = newPoints.x;
            positionY = newPoints.y;
            let canvasName;
            switch (modelId) {
                case 1:
                    canvasName = "overlayCanvas1"
                    clearCanvas(canvasName);
                    //drawTriangle(canvasName, positionX, positionY, 8, angle, 'orange');
                    drawShip(canvasName, positionX, positionY, 8, angle, 'orange');
                    drawTriangle(canvasName, ( 0 + 60+4) * 3.6 , ( 0 + 507) * 3.6 , 12,    1, 'white'); // pozycja 0 x 0
                    drawTriangle(canvasName, (77.07 + 60+4) * 3.6 , (97.25 + 507) * 3.6 , 12,    1, 'orange'); // SBM
                    drawTriangle(canvasName, (378.03 + 60+4) * 3.6 , (191.8 + 507) * 3.6 , 12,    1, 'orange'); // FPSO
                    drawTriangle(canvasName, (100 + 60+3) * 3.6 , (100 + 505) * 3.6 , 12,    1, 'white');
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 2:
                    canvasName = "overlayCanvas2"
                    clearCanvas(canvasName);
                    //drawTriangle(canvasName, positionX, positionY, 8, angle, 'blue');
                    drawShip(canvasName, positionX, positionY, 8, angle, 'blue');
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 3:
                    canvasName = "overlayCanvas3"
                    clearCanvas(canvasName);
                    //drawTriangle(canvasName, positionX, positionY, 8, angle, 'green');
                    drawShip(canvasName, positionX, positionY, 8, angle, 'green');
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 4:
                    canvasName = "overlayCanvas4"
                    clearCanvas(canvasName);
                    //drawTriangle(canvasName, positionX, positionY, 8, angle, 'purple');
                    drawShip(canvasName, positionX, positionY, 8, angle, 'purple');
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 5:
                    canvasName = "overlayCanvas5"
                    clearCanvas(canvasName);
                    //drawTriangle(canvasName, positionX, positionY, 8, angle, 'white');
                    drawShip(canvasName, positionX, positionY, 8, angle, 'white');
                    console.log("Drawing model with ID: " + modelId + " at position X: " + positionX + ", Y: " + positionY);
                    break;
                case 6:
                    canvasName = "overlayCanvas6"
                    clearCanvas(canvasName);
                    //drawTriangle(canvasName, positionX, positionY, 8, angle, 'red'); // 'blue'
                    drawShip(canvasName, positionX, positionY, 8, angle, 'red'); // 'blue'
                    drawTriangle(canvasName, ( 0 + 60+4) * 3.6 , ( 0 + 507) * 3.6 , 12,    1, 'white'); // pozycja 0 x 0
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

    // Load the background image
    const bgImg = new Image();
    bgImg.src = '/img/MapaSilm.jpg'; // Replace with the path to your background image

    bgImg.onload = function () {
        bgCtx.drawImage(bgImg, 0, 0, backgroundCanvas.width, backgroundCanvas.height);
    };

    function getScaledPoints(oldX, oldY) {
//      chart size L/G x=430 y=-60   PD x=-1570 y=1040
//      x = 430 + 1570 = 2000   ||  y = 60 + 1040 = 1100
        const bgY = backgroundCanvas.height;
        const bgX = backgroundCanvas.width;
        const staticShift_y = 505+2 /*430 + 112*/;  // 505 for /1667
        const staticShift_x = 60 +3*1;//60 + 15;    // 60+3 for /1110
        scaleX = bgX/1110;//1200;
        scaleY = bgY/1667;//1800;
        console.log("ScaleX: " + scaleX + ", ScaleY: " + scaleY);
        console.log("Old X: " + oldX + ", Old Y:  " + oldY)
        // Changed coordinate system x->y , y->x
        const y = (-oldX /*+ 430*/ + staticShift_y) * scaleY;
        const x = (oldY /*+ 60*/ + staticShift_x ) * scaleX;
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

// Function to draw a triangle
    function drawShip(elementId, x, y, scale, angle, fillColor) {
        const element = document.getElementById(elementId);
        const ctx = element.getContext('2d');
        // Define the vertices of the triangle (equilateral triangle centered at origin)
        let vertices = [
            {x: 0, y: -2},
            {x: 0.866, y: -1.0},
            {x: 0.866, y: 2.5},
            {x: -0.866, y: 2.5},
            {x: -0.866, y: -1.0}
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
};
