window.onpageshow = function () {

    if (isSamsungBrowser()) {
        alert("Samsung browser is not supported!\nSwitch to Chrome or Safari instead.")
    }

    function isSamsungBrowser() {
        return navigator.userAgent.toLocaleLowerCase().includes('samsung');
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
          ///elementsByTagNameElement.width = container.clientWidth;
          ///elementsByTagNameElement.height = container.clientHeight;
          elementsByTagNameElement.style.width = imgMap.width + 'px';
          elementsByTagNameElement.style.height =imgMap.height + 'px';
       }
    }

//    todo - do usunięcia po testach
// todo - potrzeba sprawdzić/dobrać nowy współczynnik skali
//    drawShip("overlayCanvas1", 100, 100, 10, 90, 'red', 12.21, 2, 0);                         // Warta                  ///
//    drawShip("overlayCanvas2", 200, 200, 10, 90, 'blue', 12.21, 2, 0);                        // Warta                  ///
//                                    x,y,4,angle,'blue'
//    drawTriangle("overlayCanvas1", 100, 100, 10, 90, 'blue');
//    drawTriangle("overlayCanvas2", 200, 200, 10, 90, 'red');

                  let mapa_x = 2.407; /// było 2.4   = kalibracja mapy

//    drawTriangle("overlayCanvas2", (  0    + 60+4) * mapa_x , ( 0    + 506) * mapa_x , 6,    1, 'white');         // pozycja 0 x 0             0x0
//    drawTriangle("overlayCanvas2", ( 77.07 + 60+4) * mapa_x , (97.25 + 506) * mapa_x , 6,    1, 'orange');        // SBM    -97.25x77.07
//    drawTriangle("overlayCanvas2", (378.3  + 60+4) * mapa_x , (191.8 + 506) * mapa_x , 6,    1, 'orange');        // FPSO   -191.8x378.3
//    drawTriangle("overlayCanvas2", (-25  + 64) * mapa_x , (   84 + 506) * mapa_x , 6,    1, 'red');               // <- nabieznik             -84x25
//    drawTriangle("overlayCanvas2", ( 82.8+ 64) * mapa_x , (  -69 + 506) * mapa_x , 6,    1, 'red');               // port nabieznik ->         69x82.8
//    drawTriangle("overlayCanvas2", (  2  + 64) * mapa_x , ( -130 + 506) * mapa_x , 6,    1, 'red');               // pomost Lesniczowka        130x2
//    drawTriangle("overlayCanvas2", ( 79  + 64) * mapa_x , ( -188 + 506) * mapa_x , 6,    1, 'red');               // Slip kolej END           188x79
//    drawTriangle("overlayCanvas2", (570  + 64) * mapa_x , ( -362 + 506) * mapa_x , 6,    1, 'red');               // boja kompielisko         320x570
//    drawTriangle("overlayCanvas2", (820  + 64) * mapa_x, (  610 + 506) * mapa_x , 6,    1, 'red');               // -> zatoka               -610x820
//    drawTriangle("overlayCanvas2", (926  + 64) * mapa_x , ( 1149 + 506) * mapa_x , 6,    1, 'red');               // Wiata END jeziora      -1149x926

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
                    drawShip(canvasName, positionX, positionY, 2, angle, 'orange', 12.21, 2, 0);                        // Warta
//                     x    y
//    todo - do usunięcia po testach
// todo - potrzeba sprawdzić/dobrać nowy współczynnik skali
    drawShip("overlayCanvas1", 400, 100, 10, 90, 'blue', 12.21, 2, 0);                         // Warta                  ///
    drawShip("overlayCanvas1", 500, 200, 10, 90, 'red', 12.21, 2, 0);                          // Warta                  ///
//                                    x,y,4,angle,'blue'
    drawTriangle("overlayCanvas1", 400, 100, 10, 90, 'red');
    drawTriangle("overlayCanvas1", 500, 200, 10, 90, 'blue');

                  drawTriangle("overlayCanvas1", (  0    + 60+4) * mapa_x , ( 0    + 506) * mapa_x , 6,    1, 'white');         // pozycja 0 x 0             0x0
                  drawTriangle("overlayCanvas1", ( 77.07 + 60+4) * mapa_x , (97.25 + 506) * mapa_x , 6,    1, 'orange');        // SBM    -97.25x77.07
                  drawTriangle("overlayCanvas1", (378.3  + 60+4) * mapa_x , (191.8 + 506) * mapa_x , 6,    1, 'orange');        // FPSO   -191.8x378.3
                  drawTriangle("overlayCanvas1", (-25  + 64) * mapa_x , (   84 + 506) * mapa_x , 6,    1, 'red');               // <- nabieznik             -84x25
                  drawTriangle("overlayCanvas1", ( 82.8+ 64) * mapa_x , (  -69 + 506) * mapa_x , 6,    1, 'red');               // port nabieznik ->         69x82.8
                  drawTriangle("overlayCanvas1", (  2  + 64) * mapa_x , ( -130 + 506) * mapa_x , 6,    1, 'red');               // pomost Lesniczowka        130x2
                  drawTriangle("overlayCanvas1", ( 79  + 64) * mapa_x , ( -188 + 506) * mapa_x , 6,    1, 'red');               // Slip kolej END           188x79
                  drawTriangle("overlayCanvas1", (570  + 64) * mapa_x , ( -362 + 506) * mapa_x , 6,    1, 'red');               // boja kompielisko         320x570
                  drawTriangle("overlayCanvas1", (820  + 64) * mapa_x , (  610 + 506) * mapa_x , 6,    1, 'red');               // -> zatoka               -610x820
                  drawTriangle("overlayCanvas1", (926  + 64) * mapa_x , ( 1149 + 506) * mapa_x , 6,    1, 'red');               // Wiata END jeziora      -1149x926



/*
drawShip(canvasName, 64, 506, 2, 90, 'blue', 12.21, 2, 0);                        // Warta                               ///

                    drawShip(canvasName, 100, 100, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 200, 200, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 300, 300, 23, 90, 'orange', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 400, 400, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 500, 500, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 600, 600, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 700, 700, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 800, 800, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 900, 900, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1000, 1000, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1100, 1100, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1200, 1200, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1300, 1300, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1400, 1400, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1500, 1500, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1600, 1600, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1700, 1700, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1800, 1800, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 1900, 1900, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2000, 2000, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2100, 2100, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2200, 2200, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2300, 2300, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2400, 2400, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2500, 2500, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2600, 2600, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2700, 2700, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2800, 2800, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 2900, 2900, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3000, 3000, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3100, 3100, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3200, 3200, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3300, 3300, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3400, 3400, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3500, 3500, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3600, 3600, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3700, 3700, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3800, 3800, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 3900, 3900, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                    drawShip(canvasName, 4000, 4000, 23, 90, 'red', 12.21, 2, 0);                        // Warta
                                                                                                                            */ ///
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
                    drawShip(canvasName, positionX, positionY, 2, angle, 'blue', 13.78, 2.38, 0);                       // B.L.
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
                    drawShip(canvasName, positionX, positionY, 2, angle, 'green', 11.55, 1.8, 0);                       // D.L.
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
                    drawShip(canvasName, positionX, positionY, 2, angle, 'purple', 15.5, 1.79, 0);                      // Ch.L.
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
                    drawShip(canvasName, positionX, positionY, 2, angle, 'lightgray', 10.98, 1.78, 1);                      // PROM
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
                    drawShip(canvasName, positionX, positionY, 2, angle, 'darkblue', 16.43, 2.23, 0);                       // L.M.
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
///

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
///
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
