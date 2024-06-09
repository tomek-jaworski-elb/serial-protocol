window.onload = function () {
    // Set up the canvases and their contexts
    const backgroundCanvas = document.getElementById('backgroundCanvas');
    const bgCtx = backgroundCanvas.getContext('2d');

    const overlayCanvas = document.getElementById('overlayCanvas');
    const overlayCtx = overlayCanvas.getContext('2d');
    const overlayCanvas2 = document.getElementById('overlayCanvas2');
    const overlayCtx2 = overlayCanvas.getContext('2d');
    // Set canvas dimensions to match the container
    const container = document.querySelector('.canvas-container');
    backgroundCanvas.width = container.clientWidth;
    backgroundCanvas.height = container.clientHeight;
    overlayCanvas.width = container.clientWidth;
    overlayCanvas.height = container.clientHeight;

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
            const positionX = parseFloat(data.positionX);
            const positionY = parseFloat(data.positionY);
            const angle = parseFloat(data.heading);
            const shift = 200;
            drawTriangle(overlayCtx, positionX + shift, positionY + shift, 20, angle);
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
    bgImg.src = '/img/kamionka.png'; // Replace with the path to your background image

    bgImg.onload = function () {
        bgCtx.drawImage(bgImg, 0, 0, backgroundCanvas.width, backgroundCanvas.height);
    };

    // Draw a non-typical shape (e.g., a ship waterline) on the overlay canvas
    function drawShip(ctx, x, y) {
        ctx.beginPath();
        ctx.moveTo(x, y); // Start point
        ctx.lineTo(x + 60, y); // Top edge
        ctx.lineTo(x + 70, y + 10); // Front tip
        ctx.lineTo(x + 10, y + 10); // Bottom edge
        ctx.lineTo(x, y); // Back to start
        ctx.closePath();
        ctx.fillStyle = 'blue';
        ctx.fill();
        ctx.strokeStyle = 'black';
        ctx.stroke();
    }

    // Draw a non-typical shape (e.g., a star) on the overlay canvas
    function drawStar(ctx, cx, cy, spikes, outerRadius, innerRadius) {
        let rot = Math.PI / 2 * 3;
        let x = cx;
        let y = cy;
        let step = Math.PI / spikes;

        ctx.beginPath();
        ctx.moveTo(cx, cy - outerRadius);
        for (let i = 0; i < spikes; i++) {
            x = cx + Math.cos(rot) * outerRadius;
            y = cy - Math.sin(rot) * outerRadius;
            ctx.lineTo(x, y);
            rot += step;

            x = cx + Math.cos(rot) * innerRadius;
            y = cy - Math.sin(rot) * innerRadius;
            ctx.lineTo(x, y);
            rot += step;
        }
        ctx.lineTo(cx, cy - outerRadius);
        ctx.closePath();
        ctx.fillStyle = 'gold';
        ctx.fill();
    }

    // Function to generate a random point within the canvas
    function getRandomPoint(canvas) {
        const x = Math.random() * canvas.width;
        const y = Math.random() * canvas.height;
        return {x, y};
    }

    function getRandomAngle() {
        return Math.random() * 2 * 180;
    }

    // Function to clear the first canvas
    function clearCanvas() {
        overlayCtx.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);
    }

    // Function to clear the first canvas
    function clearCanvas2() {
        overlayCtx2.clearRect(0, 0, overlayCanvas2.width, overlayCanvas2.height);
    }

    // Function to draw a circle
    function drawCircle(ctx, x, y, radius) {
        ctx.beginPath();        // Begin a new path
        ctx.arc(x, y, radius, 0, 2 * Math.PI);  // Draw an arc (circle)
        ctx.fillStyle = 'gold'; // Set the fill style
        ctx.fill();             // Fill the circle
        ctx.stroke();           // Outline the circle
    }

// Function to draw a triangle
    function drawTriangle(ctx, x, y, scale, angle) {
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
        ctx.fillStyle = 'blue';
        ctx.fill();
        ctx.strokeStyle = 'black';
        ctx.stroke();
    }

    // Draw a new star at a random position every 1 second
    // setInterval(() => {
    //     const point = getRandomPoint(overlayCanvas);
    //     const randomAngle = getRandomAngle();
    //     textField.innerText = 'Angle= ' + randomAngle + '°' + ', X=' + point.x + ', Y=' + point.y;
    //     console.log('Random angle:', randomAngle);
    //     console.log('Random point:', point);
    //     clearCanvas()
    //     drawTriangle(overlayCtx, point.x, point.y, 20, randomAngle);
    //     // drawCircle(overlayCtx, point.x, point.y, 5);
    //     // drawStar(overlayCtx, point.x, point.y, 8, 30, 15);
    // }, 1000);
};
