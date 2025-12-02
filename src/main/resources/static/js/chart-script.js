let socket;
let trackWarta = []; // Warta
let trackCherryLady = []; // Cherry Lady
let trackBlueLady = []; // Blue Lady
let trackDorchesterLady = []; // Dorchester Lady
let trackKolobrzeg = []; // Kołobrzeg
let trackLadyMarie = []; // Lady Marie

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
        elementsByTagNameElement.style.width = imgMap.width + 'px';
        elementsByTagNameElement.style.height =imgMap.height + 'px';
    } else {
        elementsByTagNameElement.width = imgMap.width;
        elementsByTagNameElement.height = imgMap.height;
        elementsByTagNameElement.style.width = imgMap.width + 'px';
        elementsByTagNameElement.style.height =imgMap.height + 'px';
    }
}

// --- tu dodajemy konvaContainer dynamicznie i stage ---
const konvaContainerId = 'konvaContainer';
let konvaStage = null;
let konvaTrackLayer = null;
let konvaShipLayer = null;

// init Konva stage as overlay (absolute) and create layers
function initKonvaOverlay() {
    // jeśli już istnieje, usuń
    const existing = document.getElementById(konvaContainerId);
    if (existing) existing.remove();

    // kontener wewnątrz .canvas-container
    const konvaDiv = document.createElement('div');
    konvaDiv.id = konvaContainerId;
    konvaDiv.style.position = 'absolute';
    konvaDiv.style.left = '0';
    konvaDiv.style.top = '0';
    konvaDiv.style.width = imgMap.width + 'px';
    konvaDiv.style.height = imgMap.height + 'px';
    // WAŻNE: musimy odbierać eventy, więc pointerEvents = 'auto'
    konvaDiv.style.pointerEvents = 'auto';
    konvaDiv.style.zIndex = 999;
    container.appendChild(konvaDiv);

    // Stworzenie stage Konva
    konvaStage = new Konva.Stage({
        container: konvaContainerId,
        width: imgMap.width,
        height: imgMap.height
    });

    // warstwa tras i warstwa statków
    konvaTrackLayer = new Konva.Layer();
    konvaShipLayer = new Konva.Layer();

    konvaStage.add(konvaTrackLayer);
    konvaStage.add(konvaShipLayer);
}

// resize konva gdy zmienia się rozmiar mapy
function resizeKonvaOverlay() {
    const konvaDiv = document.getElementById(konvaContainerId);
    if (!konvaDiv || !konvaStage) return;
    konvaDiv.style.width = imgMap.width + 'px';
    konvaDiv.style.height = imgMap.height + 'px';
    konvaStage.width(imgMap.width);
    konvaStage.height(imgMap.height);
    konvaStage.draw();
}

// --- preload obrazków LED (bez zmian) ---
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

// ------------------------------------------------------------------
// MODELS CONFIG (używane też do przechowywania obiektów Konva)
// ------------------------------------------------------------------
const modelsConfig = {
    1: { canvas: "overlayCanvas1", headingField: "heading1", speedField: "speed1", led: "led1", rsField: "rs_model1_no", track: trackWarta, scale: 2, shipParams: [12.21, 2, 0] },
    2: { canvas: "overlayCanvas2", headingField: "heading2", speedField: "speed2", led: "led2", rsField: "rs_model2_no", track: trackBlueLady, scale: 2,  shipParams: [13.78, 2.38, 0] },
    3: { canvas: "overlayCanvas3", headingField: "heading3", speedField: "speed3", led: "led3", rsField: "rs_model3_no", track: trackDorchesterLady, scale: 2,  shipParams: [11.55, 1.8, 0] },
    4: { canvas: "overlayCanvas4", headingField: "heading4", speedField: "speed4", led: "led4", rsField: "rs_model4_no", track: trackCherryLady, scale: 2,  shipParams: [15.5, 1.79, 0] },
    5: { canvas: "overlayCanvas5", headingField: "heading5", speedField: "speed5", led: "led5", rsField: "rs_model5_no", track: trackKolobrzeg, scale: 2,  shipParams: [10.98, 1.78, 1] },
    6: { canvas: "overlayCanvas6", headingField: "heading6", speedField: "speed6", led: "led6", rsField: "rs_model6_no", track: trackLadyMarie, scale: 2,  shipParams: [16.43, 2.23, 0] },
};

// kolor i informacje o modelach
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
        return ship ? ship.color.toString() : 'black';
    }
});

// ShipCounter (bez zmian)
const ShipCounter = (function () {
    const incrementMap = new Map([
        [ModelsOfShips.WARTA.id, 0],
        [ModelsOfShips.BLEUE_LADY.id, 0],
        [ModelsOfShips.DORCHERTER_LADY.id, 0],
        [ModelsOfShips.CHERRY_LADY.id, 0],
        [ModelsOfShips.KOLOBRZEG.id, 0],
        [ModelsOfShips.LADY_MARIE.id, 0]
    ]);

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
    return {
        incrementIntMap
    };
})();

// ------------------------------------------------------------------
// Konva - funkcje pomocnicze do rysowania statku i trasy
// ------------------------------------------------------------------
const ANGLE_CORRECTION = 9; // zgodnie z Twoim oryginalnym kodem

// obiekt przechowujący instancje Konva dla każdego modelu
const KonvaObjects = {};

// funkcja obliczająca wierzchołki statku (używana dla Konva)
function computeShipVerticesForKonva(x, y, scale, angle, yy, xx, pp) {
    // Twój oryginalny zbiór wierzchołków (bez kopiowania ctx)
    let vertices = [
        {x:       0, y: -0.5*yy + pp*(yy/10)},
        {x:  0.5*xx, y: -0.4*yy + pp*(yy/10)},
        {x:  0.5*xx, y:  0.5*yy + pp*(yy/10)},
        {x: -0.5*xx, y:  0.5*yy + pp*(yy/10)},
        {x: -0.5*xx, y: -0.4*yy + pp*(yy/10)}
    ];

    // Scale
    vertices = vertices.map(vertex => ({ x: vertex.x * scale * 1.1, y: vertex.y * scale }));

    // Rotate
    const radians = (angle + ANGLE_CORRECTION) * Math.PI / 180;
    vertices = vertices.map(vertex => ({
        x: vertex.x * Math.cos(radians) - vertex.y * Math.sin(radians),
        y: vertex.x * Math.sin(radians) + vertex.y * Math.cos(radians)
    }));

    // Translate
    vertices = vertices.map(vertex => ({ x: vertex.x + x, y: vertex.y + y }));

    // Konva chce płaską tablicę [x1,y1,x2,y2,...]
    const flat = [];
    vertices.forEach(v => { flat.push(v.x); flat.push(v.y); });
    return flat;
}

// Tworzymy obiekty Konva dla wszystkich modeli (linie tras + kształty statków)
function createKonvaObjectsForModels() {
    if (!konvaStage || !konvaTrackLayer || !konvaShipLayer) {
        console.error("createKonvaObjectsForModels: Konva stage/warstwy nie są zainicjowane");
        return;
    }

    for (const idString of Object.keys(modelsConfig)) {
        const id = Number(idString);
        const cfg = modelsConfig[id];

        // utwórz linię trasy (pusta na start)
        const trackLine = new Konva.Line({
            points: [],
            stroke: ModelsOfShips.getColorFromId(id),
            strokeWidth: 2,
            lineJoin: 'round',
            lineCap: 'round',
            listening: false
        });
        konvaTrackLayer.add(trackLine);

        // utwórz prosty polygon statku
        const initPoints = computeShipVerticesForKonva(0,0,cfg.scale, 0, ...cfg.shipParams);
        const shipShape = new Konva.Line({
            points: initPoints,
            fill: ModelsOfShips.getColorFromId(id),
            closed: true,
            stroke: 'black',
            strokeWidth: 1,
            listening: true // od razu pozwalamy na eventy
        });

        // handler kliknięcia statku
        shipShape.on("click", (ev) => {
            // zabezpieczenie: czy tooltip istnieje?
            if (!tooltipText || !tooltipBg || !tooltipLayer) {
                console.warn("Tooltip nie jest jeszcze zainicjowany");
                return;
            }

            // zapobiegaj propagacji do stage
            ev.cancelBubble = true;

            const modelInfo = ModelsOfShips.getValueFromId(id);
            const modelName = modelInfo ? modelInfo.name : `Model ${id}`;

            // Pobieramy aktualne dane statku
            const obj = KonvaObjects[id];
            const lastAngle = obj?.lastAngle ?? 0;
            const lastSpeed = document.getElementById(modelsConfig[id].speedField)?.textContent ?? "0";

            // Konva.Text nie obsługuje HTML, więc użyjemy prostego formatowania per linia
            tooltipText.text(` ${modelName}\n ${parseFloat(lastSpeed).toFixed(1)} kn\n ${parseFloat(lastAngle).toFixed(1)}°`);

            const mousePos = konvaStage.getPointerPosition() || { x: 0, y: 0 };

            // ustawiamy pozycję tooltipa
            const px = mousePos.x + 10;
            const py = mousePos.y + 10;
            tooltipText.position({ x: px, y: py });

            // dopasuj tło do tekstu (dodajemy padding)
            const padding = tooltipText.padding() || 6;
            tooltipBg.position({ x: px, y: py });
            tooltipBg.width(tooltipText.width() + padding * 2);
            tooltipBg.height(tooltipText.height() + padding * 2);

            tooltipBg.visible(true);
            tooltipText.visible(true);
            tooltipLayer.batchDraw();
        });

        konvaShipLayer.add(shipShape);

        KonvaObjects[id] = {
            trackLine,
            shipShape,
            lastPos: { x: 0, y: 0 },
            lastAngle: 0,
            lastSpeed: 0,
            lastHeading: 0
        };
    }
    konvaTrackLayer.draw();
    konvaShipLayer.draw();
}

// Aktualizuje Konva-owy kształt statku (pozycja i rotacja)
function updateKonvaShip(id, x, y, angle) {
    const obj = KonvaObjects[id];
    const cfg = modelsConfig[id];
    if (!obj || !cfg) return;

    // Zaktualizujemy punkty statku bazując na nowych x,y,angle
    const points = computeShipVerticesForKonva(x, y, cfg.scale, angle, ...cfg.shipParams);
    obj.shipShape.points(points);

    // odśwież warstwę (można ograniczyć do draw() raz na kilka aktualizacji)
    konvaShipLayer.batchDraw();
    obj.lastPos = { x, y };
    obj.lastAngle = angle;
}

// Aktualizuje Konva-ową linię trasy na podstawie tablicy punktów cfg.track
function updateKonvaTrack(id) {
    const obj = KonvaObjects[id];
    const cfg = modelsConfig[id];
    if (!obj || !cfg) return;

    // Konva oczekuje płaskiej tablicy punktów
    const pts = [];
    for (let p of cfg.track) {
        pts.push(p.x);
        pts.push(p.y);
    }
    obj.trackLine.points(pts);
    konvaTrackLayer.batchDraw();
}

// ------------------------------------------------------------------
// Zamiennik funkcji drawTrack / drawShip: teraz Konva będzie rysował statek i trasę.
// (Zostawiam też oryginalne drawTrack/drawShip na wypadek, jeśli gdzieś indziej się odwołujesz.)
// ------------------------------------------------------------------

// ORYGINALNE funkcje zostają (ale nie używane dla statku/trasy w wariancie B)
function drawTrack(overlayCanvas1Track, track, color) {
    // elastyczny fallback - rysowanie na zwykłym canvasie
    clearCanvas(overlayCanvas1Track);
    const element = document.getElementById(overlayCanvas1Track);
    const ctx = element.getContext('2d');
    if (track.length < 2) return;
    ctx.beginPath();
    ctx.moveTo(track[0].x, track[0].y);
    const path = new Path2D();
    path.moveTo(track[0].x, track[0].y);
    for (let i = 1; i < track.length; i++) {
        path.lineTo(track[i].x, track[i].y);
    }
    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    ctx.stroke(path);
}

function drawShip(elementId, x, y, scale, angle, fillColor, yy, xx, pp) {
    // pozostawiam oryginalną implementację jako fallback (nie powinna być wywoływana dla Konva)
    const ANGLE_CORRECTION_LOCAL = 9;
    const element = document.getElementById(elementId);
    if (!element) return;
    const ctx = element.getContext('2d');
    let vertices = [
        {x:       0, y: -0.5*yy + pp*(yy/10)},
        {x:  0.5*xx, y: -0.4*yy + pp*(yy/10)},
        {x:  0.5*xx, y:  0.5*yy + pp*(yy/10)},
        {x: -0.5*xx, y:  0.5*yy + pp*(yy/10)},
        {x: -0.5*xx, y: -0.4*yy + pp*(yy/10)}
    ];
    vertices = vertices.map(vertex => ({ x: vertex.x * scale * 1.1, y: vertex.y * scale }));
    const radians = (angle + ANGLE_CORRECTION_LOCAL) * Math.PI / 180;
    vertices = vertices.map(vertex => ({
        x: vertex.x * Math.cos(radians) - vertex.y * Math.sin(radians),
        y: vertex.x * Math.sin(radians) + vertex.y * Math.cos(radians)
    }));
    vertices = vertices.map(vertex => ({ x: vertex.x + x, y: vertex.y + y }));
    ctx.beginPath();
    ctx.moveTo(vertices[0].x, vertices[0].y);
    for (let i = 1; i < vertices.length; i++) ctx.lineTo(vertices[i].x, vertices[i].y);
    ctx.closePath();
    ctx.fillStyle = fillColor;
    ctx.fill();
    ctx.strokeStyle = 'black';
    ctx.lineWidth = 1;
    ctx.stroke();
}

// ------------------------------------------------------------------
// Funkcja do aktualizacji wyświetlania modelu - zmieniona by aktualizować Konva
// ------------------------------------------------------------------
function updateModelDisplay(config, modelId, positionX, positionY, angle, speed, blinkDuration = 250) {
    // nadal czyścimy canvas przypisany do modelu (jeśli tam jest coś rysowane)
    try {
        clearCanvas(config.canvas);
    } catch (e) {
        console.warn("clearCanvas error:", e);
    }

    fillFieldValues(config.headingField, angle);
    fillFieldValues(config.speedField, speed);
    ledBlink(config.led, blinkDuration);
    fillFieldValues0(config.rsField, ShipCounter.incrementIntMap(modelId));

    // zamiast drawShip na canvasie - aktualizujemy Konva
    if (KonvaObjects[modelId]) {
        KonvaObjects[modelId].lastSpeed = speed;
        KonvaObjects[modelId].lastHeading = angle;
        updateKonvaShip(modelId, positionX, positionY, angle);
    } else {
        // fallback: rysuj na canvasie
        drawShip(config.canvas, positionX, positionY, config.scale, angle, ModelsOfShips.getColorFromId(modelId), ...config.shipParams);
    }

    // aktualizujemy lokalny track (tablica punktów) - bez zmian
    config.track.push({ x: positionX, y: positionY });

    // zamiast rysowania track na canvasie - aktualizujemy Konva track
    if (KonvaObjects[modelId]) {
        updateKonvaTrack(modelId);
    } else {
        drawTrack(getTrackCanvasName(config.canvas), config.track, ModelsOfShips.getColorFromId(modelId));
    }

    console.log(`Drawing model with ID: ${modelId} at position X: ${positionX}, Y: ${positionY}`);
}

// ------------------------------------------------------------------
// WebSocket, skalowanie i reszta logiki - zgodnie z oryginałem (z małą poprawką aby resize Konva działał)
// ------------------------------------------------------------------
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
    const staticShift_y = 506;
    const staticShift_x = 64;
    const scaleX = mapa_x;
    const scaleY = mapa_x;
    console.log("ScaleX: " + scaleX + ", ScaleY: " + scaleY);
    console.log("Old X: " + oldX + ", Old Y:  " + oldY)
    const y = (-oldX + staticShift_y) * scaleY;
    const x = (oldY + staticShift_x ) * scaleX;
    console.log("New X: " + x + ", New Y: " +  y);
    return {x, y};
}

// Function to clear the first canvas
function clearCanvas(elementId) {
    const element = document.getElementById(elementId);
    if (!element) return;
    const context = element.getContext('2d');
    context.clearRect(0, 0, element.width, element.height);
    console.log("Clear canvas width: " + element.width + ", height: " +  element.height);
}

// ------------------------------------------------------------------
// Pozostałe pomocnicze funkcje (LED, pola) - zostawione bez zmian
// ------------------------------------------------------------------
function fillFieldValues(elementId, value) {
    const spanElement = document.getElementById(elementId);
    if (!spanElement) return;
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
    if (!spanElement) return;
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

// ------------------------------------------------------------------
// TEST funkcja runShipTest - teraz korzysta z updateModelDisplay (Konva się zaktualizuje)
// ------------------------------------------------------------------
const ENABLE_TEST_RUNS = false;

function runShipTest(id, angleQ, center_X, center_Y, step, intervalIn) {
    console.log("Starting ship and track test...");
    const centerX = center_X;
    const centerY = center_Y;
    const radius = 200;
    const steps = step;
    const interval = intervalIn;
    let angle = angleQ;
    const angleStep = (2 * Math.PI) / steps;
    const testInterval = setInterval(() => {
        const x = centerX + radius * Math.cos(angle);
        const y = centerY + radius * Math.sin(angle);
        const heading = (angle * 180 / Math.PI + 270) % 360;
        const modelId = id;
        let config = modelsConfig[modelId];
        const speed = 10;
        updateModelDisplay(config, modelId, x, y, heading, speed);

        angle += angleStep;
        if (angle >= 2 * Math.PI) {
            clearInterval(testInterval);
            console.log("Ship and track test completed successfully!");
        }
    }, interval);
}

// uruchamiamy test (odkomentuj/usun w produkcji)
if (ENABLE_TEST_RUNS) {
    runShipTest(1, 0, 400, 400, 36, 500);
    runShipTest(2, Math.PI/4, 300, 600, 63,222);
    runShipTest(3, -Math.PI/3, 600, 400, 50,333);
    runShipTest(4, -Math.PI/5, 540, 320, 70,333);
    runShipTest(5, -Math.PI/2.5, 400, 230, 70,333);
    runShipTest(6, -Math.PI/2, 333, 333, 70,333);
}

function drawTriangle(x, y, size = 20, color = "red", label = "") {
    if (!konvaShipLayer) {
        console.error("drawTriangle: konvaShipLayer nie jest zainicjowana");
        return;
    }

    const height = size * Math.sqrt(3) / 2;

    // Trójkąt
    const triangle = new Konva.Line({
        points: [
            x, y - height / 2,            // góra
            x - size / 2, y + height / 2, // lewy dół
            x + size / 2, y + height / 2  // prawy dół
        ],
        fill: color,
        closed: true,
        stroke: "black",
        strokeWidth: 1
    });

    konvaShipLayer.add(triangle);

    // Tekst pod trójkątem
    if (label) {
        const text = new Konva.Text({
            text: label,
            fontSize: 12,
            fontFamily: 'Calibri',
            fontStyle: 'bold',
            padding: 2,
            stroke: "black",
            strokeWidth: 0.99,
            fill: "gray"
        });

        // ustawienie pozycji tekstu centralnie pod trójkątem
        text.x(x - text.width() / 2);
        text.y(y + height / 2 + 5); // kilka pikseli poniżej trójkąta

        konvaShipLayer.add(text);
    }

    konvaShipLayer.draw();
}

// Zarządzanie widocznością dokumentu (WebSocket reconnect)
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

// === TOOLTIP KONVA ===
let tooltipLayer = null;
let tooltipText = null;
let tooltipBg = null;

function initTooltip() {
    if (!konvaStage) {
        console.error("initTooltip: konvaStage nie jest zainicjowane");
        return;
    }

    // jeśli już wcześniej istniał tooltip - usuń go by nie duplikować
    if (tooltipLayer) {
        tooltipLayer.destroy();
        tooltipLayer = null;
        tooltipText = null;
        tooltipBg = null;
    }

    tooltipLayer = new Konva.Layer();

    tooltipBg = new Konva.Rect({
        x: 0, y: 0,
        width: 140, height: 40,
        fill: "rgba(0,0,0,0.7)",
        cornerRadius: 6,
        visible: false
    });

    tooltipText = new Konva.Text({
        x: 0, y: 0,
        fontFamily: 'Calibri',
        text: "",
        fontSize: 14,
        fill: "white",
        padding: 6,
        visible: false
    });

    tooltipLayer.add(tooltipBg);
    tooltipLayer.add(tooltipText);
    konvaStage.add(tooltipLayer);
    tooltipLayer.draw();
}

// ukrywanie tooltipa
function hideTooltip() {
    if (!tooltipLayer || !tooltipText || !tooltipBg) return;
    tooltipBg.visible(false);
    tooltipText.visible(false);
    tooltipLayer.batchDraw();
}

// ------------------------------------------------------------------
// Inicjalizacja Konva po załadowaniu DOM (inicjujemy stage i obiekty)
// ------------------------------------------------------------------
(function initializeKonvaIfPossible() {
    try {
        initKonvaOverlay();        // tworzy stage + warstwy
        initTooltip();             // tooltip zanim podpinamy kliknięcia statków
        createKonvaObjectsForModels(); // teraz tworzymy statki (handlery mają dostęp do tooltip)
        // stage click => ukryj tooltip
        if (konvaStage) {
            konvaStage.on("click", () => {
                hideTooltip();
            });
        }
        drawTriangle( (0 + 60.0 + 4) * mapa_x, (0 + 506.0) * mapa_x, 10, "orange", "pozycja [0;0]");
        drawTriangle( (77.07 + 60 + 4) * mapa_x, (97.25 + 506) * mapa_x, 10, "yellow", "SBM");
        drawTriangle( (378.3 + 60 + 4) * mapa_x, (191.8 + 506) * mapa_x, 10, "green", "FPSO");
    } catch (e) {
        console.error("Błąd podczas inicjalizacji Konva:", e);
    }
})();
