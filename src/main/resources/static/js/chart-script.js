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
        // Function to clear the first canvas
        function clearCanvas() {
            overlayCtx.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);
        }

                // Function to clear the first canvas
                function clearCanvas2() {
                    overlayCtx2.clearRect(0, 0, overlayCanvas2.width, overlayCanvas2.height);
                }

    // Draw a new ship at a random position every 1 second
    setInterval(() => {
        const point = getRandomPoint(overlayCanvas2);
        clearCanvas2();
        drawShip(overlayCtx2, point.x, point.y);
    }, 1000);


    // Draw a new star at a random position every 1 second
    setInterval(() => {
        const point = getRandomPoint(overlayCanvas);
        drawStar(overlayCtx, point.x, point.y, 8, 30, 15);
    }, 1000);

};
