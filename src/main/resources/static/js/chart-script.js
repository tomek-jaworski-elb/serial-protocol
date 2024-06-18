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
    const hostname = window.location.hostname; // Gets the hostname of the current page
    const port = 8080;
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
            positionX = Math.abs((positionX + shiftX) * scaleX);
            positionY = Math.abs((positionY + shiftY) * scaleY);
            switch (modelId) {
                case 1:
                    clearCanvas("overlayCanvas");
                    drawTriangle('overlayCanvas', positionX, positionY, 9, angle, 'orange');
                    console.log("Drawing model with ID: " + modelId + " at position: " + positionX + ", " + positionY);
                    break;
                case 2:
                    clearCanvas("overlayCanvas1");
                    drawTriangle('overlayCanvas1', positionX, positionY, 10, angle, 'blue');
                    console.log("Drawing model with ID: " + modelId + " at position: " + positionX + ", " + positionY);
                    break;
                case 3:
                    clearCanvas("overlayCanvas2");
                    drawTriangle('overlayCanvas2', positionX, positionY, 9, angle, 'green');
                    console.log("Drawing model with ID: " + modelId + " at position: " + positionX + ", " + positionY);
                    break;
                case 4:
                    clearCanvas("overlayCanvas3");
                    drawTriangle('overlayCanvas3', positionX, positionY, 10, angle, 'purple');
                    console.log("Drawing model with ID: " + modelId + " at position: " + positionX + ", " + positionY);
                    break;
                case 5:
                    clearCanvas("overlayCanvas4");
                    drawTriangle('overlayCanvas4', positionX, positionY, 8, angle, 'white');
                    console.log("Drawing model with ID: " + modelId + " at position: " + positionX + ", " + positionY);
                    break;
                default:
                    clearCanvas("overlayCanvas4");
                    drawTriangle('overlayCanvas4', positionX, positionY, 12, angle, 'black');
                    console.log("Unknown model ID: " + modelId + " at position: " + positionX + ", " + positionY);
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
        ctx.lineWidth = 2;
        ctx.stroke();
    }

    // Draw a new star at a random position every 1 second
//    setInterval(() => {
//        const point = getRandomPoint('overlayCanvas')
//        const randomAngle = getRandomAngle();
//        textField.innerText = 'Angle= ' + randomAngle.toFixed(1) + '°' + ', X=' + point.x.toFixed(2) + ', Y=' + point.y.toFixed(2);
//        console.log('Random angle:', randomAngle);
//        console.log('Random point:', point);
//        clearCanvas('overlayCanvas')
//        drawTriangle('overlayCanvas', point.x, point.y, 22, randomAngle, 'blue');
//    }, 1000);

    // Draw a new star at a random position every 1 second
//    setInterval(() => {
//        const point = getRandomPoint('overlayCanvas2');
//        const randomAngle = getRandomAngle();
//        textField.innerText = 'Angle= ' + randomAngle.toFixed(1) + '°' + ', X=' + point.x.toFixed(2) + ', Y=' + point.y.toFixed(2);
//        console.log('Random angle:', randomAngle);
//        console.log('Random point:', point);
//        clearCanvas('overlayCanvas2')
//        drawTriangle('overlayCanvas2', point.x, point.y, 18, randomAngle, 'orange');
//    }, 1233);

//    setInterval(() => {
//        const point = getRandomPoint('overlayCanvas1');
//        const randomAngle = getRandomAngle();
//        textField.innerText = 'Angle= ' + randomAngle.toFixed(1) + '°' + ', X=' + point.x.toFixed(2) + ', Y=' + point.y.toFixed(2);
//        console.log('Random angle:', randomAngle);
//        console.log('Random point:', point);
//        clearCanvas('overlayCanvas1')
//        drawTriangle('overlayCanvas1', point.x, point.y, 18, randomAngle, 'black');
//    }, 666);

//    setInterval(() => {
//        const point = getRandomPoint('overlayCanvas3');
//        const randomAngle = getRandomAngle();
//        textField.innerText = 'Angle= ' + randomAngle.toFixed(1) + '°' + ', X=' + point.x.toFixed(2) + ', Y=' + point.y.toFixed(2);
//        console.log('Random angle:', randomAngle);
//        console.log('Random point:', point);
//        clearCanvas('overlayCanvas3')
//        drawTriangle('overlayCanvas3', point.x, point.y, 13, randomAngle, 'yellow');
//    }, 500);

//    setInterval(() => {
//        const point = getRandomPoint('overlayCanvas4');
//        const randomAngle = getRandomAngle();
//        textField.innerText = 'Angle= ' + randomAngle.toFixed(1) + '°' + ', X=' + point.x.toFixed(2) + ', Y=' + point.y.toFixed(2);
//        console.log('Random angle:', randomAngle);
//        console.log('Random point:', point);
//        clearCanvas('overlayCanvas4')
//        drawTriangle('overlayCanvas4', point.x, point.y, 10, randomAngle, 'green');
//    }, 2000);
};
