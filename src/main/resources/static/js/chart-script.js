    let socket;
    let trackWarta = []; // Warta
    let trackCherryLady = []; // Cherry Lady
    let trackBlueLady = []; // Blue Lady
    let trackDorchesterLady = []; // Blue Lady
    let trackKolobrzeg = []; // Blue Lady
    let trackLadyMarie = []; // Blue Lady

    if (isSamsungBrowser()) {
        alert("Samsung browser is not supported!\nSwitch to Chrome or Safari instead.")
    }

    function isSamsungBrowser() {
        return navigator.userAgent.toLocaleLowerCase().includes('samsung');
    }

    function getTrackCanvasName(canvasName) {
        return canvasName + "_track";
    }

    const imgMap = document.getElementById("backgroundCanvas");

    const container = document.querySelector('.canvas-container');

    for (let elementsByTagNameElement of container.getElementsByTagName('canvas')) {
       if (navigator.userAgent.includes('iPhone') || navigator.userAgent.includes('iPad') || navigator.userAgent.includes('ios')) {
          elementsByTagNameElement.width = imgMap.width;
          elementsByTagNameElement.height = imgMap.height;
          elementsByTagNameElement.style.width = imgMap.width + 'px';                                                    /// gdy zablokowane win=ok(ale zoom okna to zmiana pozycji modeli) andr-pozycja modeli ifonbai - modele cienkie po lewej stronie mapyi
          elementsByTagNameElement.style.height =imgMap.height + 'px';                                                   /// gdy odblokowane win =ok android  z-win=ok z-lin przsuniete   ifonbasia  ok
       } else {
          elementsByTagNameElement.width = imgMap.width;
          elementsByTagNameElement.height = imgMap.height;
          elementsByTagNameElement.style.width = imgMap.width + 'px';
          elementsByTagNameElement.style.height =imgMap.height + 'px';
       }
    }

    let mapa_x = 2.407; /// było 2.4   = kalibracja mapy

    function preloadImage(src) {
        return new Promise((resolve, reject) => {
            const img = new Image();
            img.onload = () => resolve(img);
            img.onerror = reject;
            img.src = src;
        });
    }

    // Ensure images are fully loaded before using them
    const imagesLoaded = Promise.all([
        preloadImage("/img/led_connection_green.bmp"),
        preloadImage("/img/led_connection_1.bmp")
    ]);

    let imgLedOn, imgLedOff;
    imagesLoaded.then(([on, off]) => {
        imgLedOn = on;
        imgLedOff = off;
    }).catch(err => console.error("Błąd ładowania obrazków:", err));

    const path = '/json';
    // Set up the text field
    const textField = document.getElementById("textField");

    function drawTrack(overlayCanvas1Track, track, color) {
        clearCanvas(overlayCanvas1Track);
        const element = document.getElementById(overlayCanvas1Track);
        const ctx = element.getContext('2d');
        if (track.length < 2) return; // No need to draw if less than 2 points

        ctx.beginPath(); // Ensures the path is started
        ctx.moveTo(track[0].x, track[0].y);

        // Use Path2D for potentially better performance
        const path = new Path2D();
        // requestAnimationFrame(drawTrack);
        path.moveTo(track[0].x, track[0].y);
        for (let i = 1; i < track.length; i++) {
            path.lineTo(track[i].x, track[i].y);
        }
        ctx.strokeStyle = color;
        ctx.lineWidth = 1;
        ctx.stroke(path);
    }

// Funkcja do aktualizacji wyświetlania modelu
    function updateModelDisplay(config, modelId, positionX, positionY, angle, speed, blinkDuration = 250) {
        clearCanvas(config.canvas);
        fillFieldValues(config.headingField, angle);
        fillFieldValues(config.speedField, speed);
        ledBlink(config.led, blinkDuration);
        fillFieldValues0(config.rsField, ShipCounter.incrementIntMap(modelId));
        drawShip(config.canvas, positionX, positionY, config.scale, angle, ModelsOfShips.getColorFromId(modelId), ...config.shipParams);
        config.track.push({ x: positionX, y: positionY });
        drawTrack(getTrackCanvasName(config.canvas), config.track, ModelsOfShips.getColorFromId(modelId));
        console.log(`Drawing model with ID: ${modelId} at position X: ${positionX}, Y: ${positionY}`);
    }

function createWebSocket() {
    const ws = new WebSocket(`${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.hostname}:${window.location.port}${path}`);

    ws.onmessage = function (event) {
    console.log("WebSocket message received: ", event.data);
    textField.textContent = event.data;

    try {
        const data = JSON.parse(event.data);
        const modelId = Number(data.modelName);
        let positionX = parseFloat(data.positionX);
        let positionY = parseFloat(data.positionY);
        const angle = parseFloat(data.heading);
        const speed = parseFloat(data.speed);

        // Skalowanie punktów
        const newPoints = getScaledPoints(positionX, positionY);
        positionX = newPoints.x;
        positionY = newPoints.y;

        const config = modelsConfig[modelId];

        if (config) {
            updateModelDisplay(config, modelId, positionX, positionY, angle, speed);
        } else {
            console.log(`Unknown model ID: ${modelId} at position X: ${positionX}, Y: ${positionY}`);
        }

    } catch (error) {
        console.error("Error parsing JSON data:", error);
    }
};

    ws.onerror = function (error) {
        console.error("WebSocket error: ", error);
    };

    ws.onopen = function (event) {
        console.log("WebSocket connection opened.");
    };

    ws.onclose = function (event) {
        console.log("WebSocket connection closed.");
    };
    return ws;
}
    socket = createWebSocket();

    function getScaledPoints(oldX, oldY) {
        const staticShift_y = 506;                                                                                      // 506
        const staticShift_x = 64;                                                                                       //  64
        scaleX = mapa_x; /// 2.4; // 3.61 / 1.5 ; /// 1;// 3.61;                   // 2.407                                                                               //   3.61
        scaleY = mapa_x; /// 2.4; // 3.61 / 1.5 ; /// 1;// 3.61;                                                                                                  //   3.61
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
        console.log("Clear canvas width: " + element.width + ", height: " +  element.height);
    }

// Function to draw a Ship
    function drawShip(elementId, x, y, scale, angle, fillColor, yy, xx, pp) {
        const ANGLE_CORRECTION = 9; // Add 9 degrees to the angle for correct display of the ship
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
        const radians = (angle + ANGLE_CORRECTION) * Math.PI / 180;
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

    function fillFieldValues(elementId, value) {
        const spanElement = document.getElementById(elementId);
        if (String(elementId).includes("heading")) {
            spanElement.innerHTML = value.toFixed(1).padStart(4, '0');
            console.log("Heading: " + value.toFixed(1).padStart(4, '0'));
        } else {
            spanElement.innerHTML = value.toFixed(1);
            console.log("Speed: " + value.toFixed(1));
        }
    }

    function fillFieldValues0(elementId, value) {
        const spanElement = document.getElementById(elementId);
        spanElement.innerHTML = value;
    }

    function ledBlink(elementId, duration) {
        if (!imgLedOn || !imgLedOff) {
            console.warn("Obrazy jeszcze się nie załadowały");
            return;
        }

        const element = document.getElementById(elementId);
        if (!element) {
            console.error(`Element z ID '${elementId}' nie istnieje`);
            return;
        }

        element.src = imgLedOn.src;
        setTimeout(() => {
            element.src = imgLedOff.src;
        }, duration);
    }

    // Test function to verify drawShip and drawTrack functionality
    function runShipTest() {
        console.log("Starting ship and track test...");
        
        // Test parameters
        const centerX = 400;  // Center of the test area
        const centerY = 400;
        const radius = 200;   // Radius of the circular path
        const steps = 36;     // Number of steps for a full circle
        const interval = 500; // Time between steps in ms

        let angle = 0;
        const angleStep = (2 * Math.PI) / steps;

        // Start the test loop
        const testInterval = setInterval(() => {
            // Calculate new position
            const x = centerX + radius * Math.cos(angle);
            const y = centerY + radius * Math.sin(angle);
            const heading = (angle * 180 / Math.PI + 270) % 360; // Convert to degrees and adjust for ship orientation
            const modelId = 2;
            let config = modelsConfig[modelId];
            const speed = 10;
            updateModelDisplay(config, modelId, x, y, angle, speed);

            // Update angle for next step
            angle += angleStep;
            
            // Stop after one full circle
            if (angle >= 2 * Math.PI) {
                clearInterval(testInterval);
                console.log("Ship and track test completed successfully!");
            }
        }, interval);
    }
    
    // Uncomment the line below to run the test automatically when the script loads
     runShipTest();
    
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

// Konfiguracja modeli
const modelsConfig = {
            1: { canvas: "overlayCanvas1", headingField: "heading1", speedField: "speed1", led: "led1", rsField: "rs_model1_no", track: trackWarta, scale: 2, shipParams: [12.21, 2, 0] },
            2: { canvas: "overlayCanvas2", headingField: "heading2", speedField: "speed2", led: "led2", rsField: "rs_model2_no", track: trackBlueLady, scale: 2,  shipParams: [13.78, 2.38, 0] },
            3: { canvas: "overlayCanvas3", headingField: "heading3", speedField: "speed3", led: "led3", rsField: "rs_model3_no", track: trackDorchesterLady, scale: 2,  shipParams: [11.55, 1.8, 0] },
            4: { canvas: "overlayCanvas4", headingField: "heading4", speedField: "speed4", led: "led4", rsField: "rs_model4_no", track: trackCherryLady, scale: 2,  shipParams: [15.5, 1.79, 0] },
            5: { canvas: "overlayCanvas5", headingField: "heading5", speedField: "speed5", led: "led5", rsField: "rs_model5_no", track: trackKolobrzeg, scale: 2,  shipParams: [10.98, 1.78, 1] },
            6: { canvas: "overlayCanvas6", headingField: "heading6", speedField: "speed6", led: "led6", rsField: "rs_model6_no", track: trackLadyMarie, scale: 2,  shipParams: [16.43, 2.23, 0] },
        };

const ModelsOfShips = Object.freeze({
    WARTA: {id: 1, color: "orange", name: "Warta"},
    BLEUE_LADY: {id: 2, color: "blue", name: "Blue Lady"},
    DORCHERTER_LADY: {id: 3, color: "green", name: "Dorchester Lady"},
    CHERRY_LADY: {id: 4, color: "purple", name: "Cherry Lady"},
    KOLOBRZEG: {id: 5, color: "lightgray", name: "Kołobrzeg"},
    LADY_MARIE: {id: 6, color: "darkblue", name: "Lady Marie"},

    getValueFromId(id) {
        return Object.values(ModelsOfShips).find(ship => ship.id === id);
    },

    getColorFromId(id) {
        const ship = ModelsOfShips.getValueFromId(id);
        return ship ? ship.color.toString() : 'black'; // Return the color or black if not found
    }
});

    const ShipCounter = (function () {
        // Private map
        const incrementMap = new Map([
            [ModelsOfShips.WARTA.id, 0],
            [ModelsOfShips.BLEUE_LADY.id, 0],
            [ModelsOfShips.DORCHERTER_LADY.id, 0],
            [ModelsOfShips.CHERRY_LADY.id, 0],
            [ModelsOfShips.KOLOBRZEG.id, 0],
            [ModelsOfShips.LADY_MARIE.id, 0]
        ]);

        // Public function to increment the map's values
        function incrementIntMap(key) {
            if (incrementMap.has(key)) {
                incrementMap.set(key, incrementMap.get(key) + 1);
                if (incrementMap.get(key) > 999) {
                    incrementMap.set(key, 0);
                }
                return incrementMap.get(key);
            } else {
                console.error("Key " + key + " does not exist in map");
            }
        }

        // Expose only the incrementIntMap function
        return {
            incrementIntMap
        };
    })();