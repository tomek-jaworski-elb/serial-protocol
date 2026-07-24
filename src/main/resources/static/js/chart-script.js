let socket;

if (isSamsungBrowser()) {
    alert("Samsung browser is not supported!\nSwitch to Chrome or Safari instead.")
}

function isSamsungBrowser() {
    return navigator.userAgent.toLocaleLowerCase().includes('samsung');
}

const container = document.querySelector('.canvas-container');

// --- Konva stage: map as bottom layer + zoom/pan/pinch ---
const konvaContainerId = 'konvaContainer';
const MAP_WIDTH = 2666;
const MAP_HEIGHT = 4000;
// modern formats first, JPG as last-resort fallback
const MAP_IMAGE_SOURCES = [
    '/img/MapaSilm_2666x4000.avif',
    '/img/MapaSilm_2666x4000.webp',
    '/img/MapaSilm_2666x4000.jpg'
];
const SCALE_BY = 1.1;
const MAX_SCALE = 3;
let minScale = 0.1; // recomputed in fitStageToContainer() so the whole map always fits

let konvaStage = null;
let konvaMapLayer = null;
let konvaTrackLayer = null;
let konvaShipLayer = null;

// try each source in order, resolve with the first that loads
function loadMapImage(sources) {
    return new Promise((resolve, reject) => {
        const tryNext = (index) => {
            if (index >= sources.length) {
                reject(new Error('Unable to load map image: ' + sources.join(', ')));
                return;
            }
            const img = new Image();
            img.onload = () => resolve(img);
            img.onerror = () => tryNext(index + 1);
            img.src = sources[index];
        };
        tryNext(0);
    });
}

function clampScale(scale) {
    return Math.max(minScale, Math.min(MAX_SCALE, scale));
}

// tap target size for ship shapes, in screen pixels; hitStrokeWidth is expressed
// in map units, so it must be rescaled on every zoom change to stay tappable
const SHIP_HIT_SCREEN_PX = 25;

function updateShipHitAreas() {
    if (!konvaStage) return;
    const scale = konvaStage.scaleX();
    for (const id of Object.keys(KonvaObjects)) {
        KonvaObjects[id].shipShape.hitStrokeWidth(SHIP_HIT_SCREEN_PX / scale);
    }
}

// keep the map covering the viewport; center it when smaller than the viewport
function clampStagePosition(pos, scale) {
    const cw = konvaStage.width();
    const ch = konvaStage.height();
    const mapW = MAP_WIDTH * scale;
    const mapH = MAP_HEIGHT * scale;
    const x = mapW <= cw ? (cw - mapW) / 2 : Math.min(0, Math.max(cw - mapW, pos.x));
    const y = mapH <= ch ? (ch - mapH) / 2 : Math.min(0, Math.max(ch - mapH, pos.y));
    return {x, y};
}

// resize stage to container; resetView=true fits the map to the full container width
// (initial view); zooming out further, down to the whole map, is still possible
function fitStageToContainer(resetView) {
    if (!konvaStage) return;
    konvaStage.width(container.clientWidth);
    konvaStage.height(container.clientHeight);
    minScale = Math.min(konvaStage.width() / MAP_WIDTH, konvaStage.height() / MAP_HEIGHT);
    const fitWidthScale = clampScale(konvaStage.width() / MAP_WIDTH);
    const scale = resetView ? fitWidthScale : clampScale(konvaStage.scaleX());
    konvaStage.scale({x: scale, y: scale});
    konvaStage.position(clampStagePosition(konvaStage.position(), scale));
    updateShipHitAreas();
    konvaStage.batchDraw();
}

// zoom keeping the given viewport point fixed (mouse cursor / pinch center)
function zoomStageAtPoint(pointer, newScaleRaw) {
    const oldScale = konvaStage.scaleX();
    const newScale = clampScale(newScaleRaw);
    const mapPoint = {
        x: (pointer.x - konvaStage.x()) / oldScale,
        y: (pointer.y - konvaStage.y()) / oldScale
    };
    konvaStage.scale({x: newScale, y: newScale});
    konvaStage.position(clampStagePosition({
        x: pointer.x - mapPoint.x * newScale,
        y: pointer.y - mapPoint.y * newScale
    }, newScale));
    updateShipHitAreas();
    konvaStage.batchDraw();
    if (currentTooltipShipId !== null) {
        updateTooltipContent(currentTooltipShipId);
    }
}

function bindStageZoom() {
    konvaStage.on('wheel', (e) => {
        e.evt.preventDefault();
        const pointer = konvaStage.getPointerPosition();
        if (!pointer) return;
        const oldScale = konvaStage.scaleX();
        zoomStageAtPoint(pointer, e.evt.deltaY > 0 ? oldScale / SCALE_BY : oldScale * SCALE_BY);
    });
}

// two-finger pinch zoom (mobile)
let lastPinchDist = 0;

function bindStagePinch() {
    // Native DOM listeners on `container`, not konvaStage.on(): Konva does not reliably
    // dispatch its own 'touchstart'/'touchmove' bus events while a drag is active, so relying
    // on the Konva event bus here silently drops every pinch gesture that starts as (or
    // overlaps with) a single-finger drag — the normal way real pinch gestures begin.
    container.addEventListener('touchstart', (e) => {
        if (e.touches.length >= 2 && konvaStage.isDragging()) {
            konvaStage.stopDrag();
        }
    }, {passive: true});

    container.addEventListener('touchmove', (e) => {
        const touch1 = e.touches[0];
        const touch2 = e.touches[1];
        if (!touch1 || !touch2) return;
        e.preventDefault();
        if (konvaStage.isDragging()) {
            konvaStage.stopDrag();
        }
        const dist = Math.hypot(touch2.clientX - touch1.clientX, touch2.clientY - touch1.clientY);
        if (!lastPinchDist) {
            lastPinchDist = dist;
            return;
        }
        const rect = container.getBoundingClientRect();
        const pointer = {
            x: (touch1.clientX + touch2.clientX) / 2 - rect.left,
            y: (touch1.clientY + touch2.clientY) / 2 - rect.top
        };
        zoomStageAtPoint(pointer, konvaStage.scaleX() * (dist / lastPinchDist));
        lastPinchDist = dist;
    }, {passive: false});

    container.addEventListener('touchend', () => {
        lastPinchDist = 0;
    }, {passive: true});
}

// init Konva stage with map image as bottom layer
function initKonvaStage() {
    // small mouse jitter on click must not start a stage drag (it would swallow ship clicks)
    Konva.dragDistance = 3;
    konvaStage = new Konva.Stage({
        container: konvaContainerId,
        width: container.clientWidth,
        height: container.clientHeight,
        draggable: true
    });
    konvaStage.dragBoundFunc((pos) => clampStagePosition(pos, konvaStage.scaleX()));

    konvaMapLayer = new Konva.Layer({listening: false});
    konvaTrackLayer = new Konva.Layer({listening: false});
    konvaShipLayer = new Konva.Layer();

    konvaStage.add(konvaMapLayer);
    konvaStage.add(konvaTrackLayer);
    konvaStage.add(konvaShipLayer);

    loadMapImage(MAP_IMAGE_SOURCES)
        .then((img) => {
            konvaMapLayer.add(new Konva.Image({
                image: img,
                x: 0,
                y: 0,
                width: MAP_WIDTH,
                height: MAP_HEIGHT,
                listening: false
            }));
            konvaMapLayer.batchDraw();
        })
        .catch((err) => console.error('Map image loading failed:', err));

    fitStageToContainer(true);
    bindStageZoom();
    bindStagePinch();
    bindMapControls();

    // double click / double tap resets the view to the whole map
    konvaStage.on('dblclick dbltap', () => fitStageToContainer(true));
}

// zoom buttons overlay (+ / - / reset)
function bindMapControls() {
    const zoomInBtn = document.getElementById('zoomInBtn');
    const zoomOutBtn = document.getElementById('zoomOutBtn');
    const zoomResetBtn = document.getElementById('zoomResetBtn');
    const viewportCenter = () => ({x: konvaStage.width() / 2, y: konvaStage.height() / 2});
    if (zoomInBtn) zoomInBtn.addEventListener('click', () => zoomStageAtPoint(viewportCenter(), konvaStage.scaleX() * 1.3));
    if (zoomOutBtn) zoomOutBtn.addEventListener('click', () => zoomStageAtPoint(viewportCenter(), konvaStage.scaleX() / 1.3));
    if (zoomResetBtn) zoomResetBtn.addEventListener('click', () => fitStageToContainer(true));
}

// Throttling dla resize okna
let resizeKonvaTimeout = null;
function throttledResizeKonvaOverlay() {
    if (resizeKonvaTimeout) clearTimeout(resizeKonvaTimeout);
    resizeKonvaTimeout = setTimeout(() => {
        fitStageToContainer(false);
        resizeKonvaTimeout = null;
    }, 100);
}
window.addEventListener('resize', throttledResizeKonvaOverlay);

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
    preloadImage("/img/led_connection_green.png"),
    preloadImage("/img/led_connection_1.png")
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
    1: { headingField: "heading1", speedField: "speed1", led: "led1", rsField: "rs_model1_no", track: [], scale: 2, shipParams: [12.21, 2, 0] },
    2: { headingField: "heading2", speedField: "speed2", led: "led2", rsField: "rs_model2_no", track: [], scale: 2, shipParams: [13.78, 2.38, 0] },
    3: { headingField: "heading3", speedField: "speed3", led: "led3", rsField: "rs_model3_no", track: [], scale: 2, shipParams: [11.55, 1.8, 0] },
    4: { headingField: "heading4", speedField: "speed4", led: "led4", rsField: "rs_model4_no", track: [], scale: 2, shipParams: [15.5, 1.79, 0] },
    5: { headingField: "heading5", speedField: "speed5", led: "led5", rsField: "rs_model5_no", track: [], scale: 2, shipParams: [10.98, 1.78, 1] },
    6: { headingField: "heading6", speedField: "speed6", led: "led6", rsField: "rs_model6_no", track: [], scale: 2, shipParams: [16.43, 2.23, 0] },
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
        const initPoints = computeShipVerticesForKonva(0, 0, cfg.scale, 0, ...cfg.shipParams);
        const shipShape = new Konva.Line({
            points: initPoints,
            fill: ModelsOfShips.getColorFromId(id),
            closed: true,
            stroke: 'black',
            strokeWidth: 1,
            listening: true, // od razu pozwalamy na eventy
            hitStrokeWidth: SHIP_HIT_SCREEN_PX / konvaStage.scaleX() // constant screen-size tap target
        });

        // handler kliknięcia statku
        shipShape.on("click tap", (ev) => {
            // zabezpieczenie: czy tooltip istnieje?
            if (!tooltipTexts || tooltipTexts.length === 0 || !tooltipBg || !tooltipLayer) {
                console.warn("Tooltip nie jest jeszcze zainicjowany");
                return;
            }

            // zapobiegaj propagacji do stage
            ev.cancelBubble = true;

            currentTooltipShipId = id;

            // Ustaw początkowy tooltip
            updateTooltipContent(id);

            tooltipBg.visible(true);
            for (let t of tooltipTexts) {
                t.visible(true);
            }

            // Uruchom interwał do aktualizacji (dla zmiany czasu)
            if (tooltipUpdateInterval) clearInterval(tooltipUpdateInterval);
            tooltipUpdateInterval = setInterval(() => {
                if (currentTooltipShipId === id) {
                    updateTooltipContent(id);
                }
            }, 100);
        });

        konvaShipLayer.add(shipShape);

        KonvaObjects[id] = {
            trackLine,
            shipShape,
            lastPos: { x: 0, y: 0 },
            lastAngle: 0,
            lastSpeed: 0,
            lastHeading: 0,
            lastUpdateTime: null
        };
    }
    konvaTrackLayer.draw();
    konvaShipLayer.draw();
}

// Aktualizuje Konva-owy kształt statku (pozycja i rotacja)
// batchDraw() koalescuje rysowanie przez requestAnimationFrame - licznik zbędny
function updateKonvaShip(id, x, y, angle) {
    const obj = KonvaObjects[id];
    const cfg = modelsConfig[id];
    if (!obj || !cfg) return;

    // Zaktualizujemy punkty statku bazując na nowych x,y,angle
    // cfg.scale is constant: silhouette size reflects the real ship size relative to the map at any zoom
    const points = computeShipVerticesForKonva(x, y, cfg.scale, angle, ...cfg.shipParams);
    obj.shipShape.points(points);
    konvaShipLayer.batchDraw();
    obj.lastPos = { x, y };
    obj.lastAngle = angle;

    // Jeśli tooltip jest widoczny dla tego statku, zaktualizuj jego zawartość i pozycję natychmiast
    if (currentTooltipShipId === id && tooltipTexts && tooltipTexts.length > 0 && tooltipBg && tooltipLayer) {
        // Aktualizuj zawartość tooltip (wartości, czas)
        updateTooltipContent(id);
    }
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
// Funkcja do aktualizacji wyświetlania modelu
// ------------------------------------------------------------------
function updateModelDisplay(config, modelId, positionX, positionY, angle, speed, blinkDuration = 250) {
    fillFieldValues(config.headingField, angle);
    fillFieldValues(config.speedField, speed);
    ledBlink(config.led, blinkDuration);
    fillFieldValues0(config.rsField, ShipCounter.incrementIntMap(modelId));

    if (KonvaObjects[modelId]) {
        KonvaObjects[modelId].lastSpeed = speed;
        KonvaObjects[modelId].lastHeading = angle;
        KonvaObjects[modelId].lastUpdateTime = Date.now();
        updateKonvaShip(modelId, positionX, positionY, angle);
    }

    config.track.push({ x: positionX, y: positionY });
    if (config.track.length > 1000) {
        config.track.splice(0, config.track.length - 1000);
    }

    if (KonvaObjects[modelId]) {
        updateKonvaTrack(modelId);
    }
}

// ------------------------------------------------------------------
// WebSocket with automatic reconnect (exponential backoff)
// ------------------------------------------------------------------
const WS_RECONNECT_BASE_DELAY = 1000;
const WS_RECONNECT_MAX_DELAY = 30000;
let wsReconnectDelay = WS_RECONNECT_BASE_DELAY;
let wsReconnectTimer = null;

function createWebSocket() {
    const ws = new WebSocket(`${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.hostname}:${window.location.port}${path}`);

    ws.onmessage = function (event) {
        try {
            const data = JSON.parse(event.data);
            const modelId = Number(data.modelName);
            let positionX = parseFloat(data.positionX);
            let positionY = parseFloat(data.positionY);
            const angle = parseFloat(data.heading);
            const speed = parseFloat(data.speed);

            const modelInfo = ModelsOfShips.getValueFromId(modelId);
            const modelName = modelInfo ? modelInfo.name : `Model ${modelId}`;
            textField.textContent = `${modelName} | Pos: ${positionX.toFixed(2)}, ${positionY.toFixed(2)} | Speed: ${speed.toFixed(1)} kn | Heading: ${angle.toFixed(1)}°`;

            const newPoints = getScaledPoints(positionX, positionY);
            positionX = newPoints.x;
            positionY = newPoints.y;

            const config = modelsConfig[modelId];

            if (config) {
                updateModelDisplay(config, modelId, positionX, positionY, angle, speed);
            }

        } catch (error) {
            console.error("Error parsing JSON data:", error);
        }
    };

    ws.onerror = function (error) {
        console.error("WebSocket error: ", error);
    };

    ws.onopen = function (event) {
        wsReconnectDelay = WS_RECONNECT_BASE_DELAY;
    };

    ws.onclose = function (event) {
        scheduleReconnect();
    };
    return ws;
}

function scheduleReconnect() {
    if (document.hidden) return;
    if (wsReconnectTimer) return;
    wsReconnectTimer = setTimeout(() => {
        wsReconnectTimer = null;
        if (!socket || socket.readyState === WebSocket.CLOSED) {
            socket = createWebSocket();
        }
        wsReconnectDelay = Math.min(wsReconnectDelay * 2, WS_RECONNECT_MAX_DELAY);
    }, wsReconnectDelay);
}
socket = createWebSocket();

function getScaledPoints(oldX, oldY) {
    const staticShift_y = 506;
    const staticShift_x = 64;
    const scaleX = mapa_x;
    const scaleY = mapa_x;
    const y = (-oldX + staticShift_y) * scaleY;
    const x = (oldY + staticShift_x ) * scaleX;
    return {x, y};
}

// ------------------------------------------------------------------
// Pozostałe pomocnicze funkcje (LED, pola)
// ------------------------------------------------------------------
const domElementCache = {};
function getCachedElement(id) {
    if (!domElementCache[id]) {
        domElementCache[id] = document.getElementById(id);
    }
    return domElementCache[id];
}

function fillFieldValues(elementId, value) {
    const spanElement = getCachedElement(elementId);
    if (!spanElement) return;
    if (String(elementId).includes("heading")) {
        spanElement.innerHTML = value.toFixed(1).padStart(4, '0');
    } else {
        spanElement.innerHTML = value.toFixed(1);
    }
}

function fillFieldValues0(elementId, value) {
    const spanElement = getCachedElement(elementId);
    if (!spanElement) return;
    spanElement.innerHTML = value;
}

function ledBlink(elementId, duration) {
    if (!imgLedOn || !imgLedOff) return;
    const element = getCachedElement(elementId);
    if (!element) return;
    element.src = imgLedOn.src;
    setTimeout(() => {
        element.src = imgLedOff.src;
    }, duration);
}

// ------------------------------------------------------------------
// TEST funkcja runShipTest - teraz korzysta z updateModelDisplay (Konva się zaktualizuje)
// ------------------------------------------------------------------
const ENABLE_TEST_RUNS = ENABLE_TEST_RUNS_VAR;
const ENABLE_TRIANGLE_RUNS = ENABLE_TEST_RUNS_VAR;

function runShipTest(id, angleQ, center_X, center_Y, step, intervalIn) {
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
        }
    }, interval);
}

// uruchamiamy test (odkomentuj/usun w produkcji)
if (ENABLE_TEST_RUNS) {
    runShipTest(1, 0, 400, 400, 36, 2000);
    runShipTest(2, Math.PI/4, 300, 600, 63,1500);
    runShipTest(3, -Math.PI/3, 600, 400, 50,2500);
    runShipTest(4, -Math.PI/5, 540, 320, 70,1333);
    runShipTest(5, -Math.PI/2.5, 400, 230, 70,2800);
    runShipTest(6, -Math.PI/2, 333, 333, 70,750);
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
        if (wsReconnectTimer) {
            clearTimeout(wsReconnectTimer);
            wsReconnectTimer = null;
        }
        if (socket) {
            socket.close();
        }
    } else {
        wsReconnectDelay = WS_RECONNECT_BASE_DELAY;
        if (!socket || socket.readyState === WebSocket.CLOSED) {
            socket = createWebSocket();
        }
    }
});

// === TOOLTIP KONVA ===
function getTimeAgo(timestamp) {
    const now = Date.now();
    const diffMs = now - timestamp;
    const seconds = diffMs / 1000;
    if (seconds < 60) return `${seconds.toFixed(1)} seconds ago`;
    const minutes = seconds / 60;
    if (minutes < 60) return `${minutes.toFixed(1)} minutes ago`;
    const hours = minutes / 60;
    return `${hours.toFixed(1)} hours ago`;
}

let tooltipLayer = null;
let tooltipTexts = []; // tablica dla tekstu nazwy (bold) i pozostałych linii
let tooltipBg = null;
let tooltipUpdateInterval = null;
let currentTooltipShipId = null;

// Funkcja do aktualizacji zawartości tooltip na podstawie ID statku
function updateTooltipContent(id) {
    if (!tooltipTexts || tooltipTexts.length === 0 || !tooltipBg || !tooltipLayer) return;

    const obj = KonvaObjects[id];
    const cfg = modelsConfig[id];
    if (!obj || !cfg) return;

    const modelInfo = ModelsOfShips.getValueFromId(id);
    const modelName = modelInfo ? modelInfo.name : `Model ${id}`;

    // Pobieramy aktualne dane z obiektu Konva (zawsze świeże wartości)
    const lastAngle = obj.lastAngle ?? 0;
    const lastSpeed = document.getElementById(cfg.speedField)?.textContent ?? "0";
    const lastUpdateTime = obj.lastUpdateTime;
    const updateStr = lastUpdateTime ? `Updated: ${getTimeAgo(lastUpdateTime)}` : 'No update';

    // Dane do wyświetlenia - teraz każdy element to tablica [tekst, czyBold]
    const contentData = [
        [{ text: modelName, isBold: true }],  // nazwa statku - jeden element
        [{ text: "Speed:", isBold: false }, { text: `${parseFloat(lastSpeed).toFixed(1)} kn`, isBold: true }],  // Speed: etykieta normalna + wartość pogrubiona
        [{ text: "Heading:", isBold: false }, { text: `${parseFloat(lastAngle).toFixed(1)}°`, isBold: true }],  // Heading: etykieta normalna + wartość pogrubiona
        [{ text: "Updated:", isBold: false }, { text: lastUpdateTime ? getTimeAgo(lastUpdateTime) : 'No update', isBold: true }]  // Updated: etykieta normalna + wartość pogrubiona
    ];

    // Aktualizuj teksty
    let textIndex = 0;
    for (let line of contentData) {
        for (let item of line) {
            if (tooltipTexts[textIndex]) {
                tooltipTexts[textIndex].text(item.text);
                tooltipTexts[textIndex].fontStyle(item.isBold ? 'bold' : 'normal');
            }
            textIndex++;
        }
    }

    // Oblicz wymiary tooltip na podstawie wszystkich tekstów
    const padding = 8;
    let maxWidth = 0;
    let totalHeight = 0;

    for (let t of tooltipTexts) {
        maxWidth = Math.max(maxWidth, t.width());
        totalHeight += t.height();
    }

    const lineSpacing = 4;
    totalHeight += lineSpacing * (tooltipTexts.length - 1);

    tooltipBg.width(maxWidth + padding * 2);
    tooltipBg.height(totalHeight + padding * 2);

    // counter-scale the tooltip layer so the tooltip keeps constant screen size at any zoom;
    // layer coords = map coords * stageScale (net transform = 1:1 screen px + stage offset)
    const stageScale = konvaStage.scaleX();
    tooltipLayer.scale({x: 1 / stageScale, y: 1 / stageScale});

    // Aktualizuj pozycję tooltip na podstawie pozycji statku
    const shipPos = obj.lastPos;
    const px = shipPos.x * stageScale + 10;
    const py = shipPos.y * stageScale + 10;

    // Ustawianie pozycji dla każdego tekstu
    let currentY = py + padding;
    for (let t of tooltipTexts) {
        t.position({ x: px + padding, y: currentY });
        currentY += t.height() + lineSpacing;
    }

    tooltipBg.position({ x: px, y: py });

    tooltipLayer.batchDraw();
}

function initTooltip() {
    if (!konvaStage) {
        console.error("initTooltip: konvaStage nie jest zainicjowane");
        return;
    }

    // jeśli już wcześniej istniał tooltip - usuń go by nie duplikować
    if (tooltipLayer) {
        tooltipLayer.destroy();
        tooltipLayer = null;
        tooltipTexts = [];
        tooltipBg = null;
    }

    tooltipLayer = new Konva.Layer();

    tooltipBg = new Konva.Rect({
        x: 0, y: 0,
        width: 0, height: 0,
        fill: "rgba(0,0,0,0.7)",
        cornerRadius: 6,
        visible: false
    });

    // Dodaj tło PIERWSZE (będzie na dnie)
    tooltipLayer.add(tooltipBg);

    // Tworzenie 8 tekstów: nazwa + Speed (etykieta + wartość) + Heading (etykieta + wartość) + Updated (etykieta + wartość)
    tooltipTexts = [];
    const textConfigs = [
        { isBold: true, fontSize: 15 },   // nazwa statku
        { isBold: false, fontSize: 13 },  // Speed: etykieta
        { isBold: true, fontSize: 13 },   // wartość Speed
        { isBold: false, fontSize: 13 },  // Heading: etykieta
        { isBold: true, fontSize: 13 },   // wartość Heading
        { isBold: false, fontSize: 12 },  // Updated: etykieta
        { isBold: true, fontSize: 12 }    // wartość Updated
    ];

    for (let i = 0; i < textConfigs.length; i++) {
        const config = textConfigs[i];
        const txt = new Konva.Text({
            x: 0, y: 0,
            fontFamily: 'Calibri',
            fontStyle: config.isBold ? 'bold' : 'normal',
            text: "",
            fontSize: config.fontSize,
            fill: "white",
            visible: false
        });
        tooltipLayer.add(txt);
        tooltipTexts.push(txt);
    }

    konvaStage.add(tooltipLayer);
    tooltipLayer.draw();
}

// ukrywanie tooltipa
function hideTooltip() {
    if (!tooltipLayer || !tooltipBg) return;
    tooltipBg.visible(false);
    for (let t of tooltipTexts) {
        t.visible(false);
    }
    tooltipLayer.batchDraw();
    if (tooltipUpdateInterval) {
        clearInterval(tooltipUpdateInterval);
        tooltipUpdateInterval = null;
    }
    currentTooltipShipId = null;
}

// ------------------------------------------------------------------
// Inicjalizacja Konva po załadowaniu DOM (inicjujemy stage i obiekty)
// ------------------------------------------------------------------
(function initializeKonvaIfPossible() {
    try {
        initKonvaStage();          // tworzy stage + warstwy + mapę + zoom/pan
        initTooltip();             // tooltip zanim podpinamy kliknięcia statków
        createKonvaObjectsForModels(); // teraz tworzymy statki (handlery mają dostęp do tooltip)
        // stage click => ukryj tooltip
        if (konvaStage) {
            konvaStage.on("click tap", () => {
                hideTooltip();
            });
        }
        if (ENABLE_TRIANGLE_RUNS) {
            drawTriangle((0 + 60.0 + 4) * mapa_x, (0 + 506.0) * mapa_x, 10, "orange", "pozycja [0;0]");
            drawTriangle((77.07 + 60 + 4) * mapa_x, (97.25 + 506) * mapa_x, 10, "yellow", "SBM"); // SBM    -97.25x77.07
            drawTriangle((378.3 + 60 + 4) * mapa_x, (191.8 + 506) * mapa_x, 10, "green", "FPSO"); // FPSO   -191.8x378.3
            drawTriangle((-25 + 64) * mapa_x, (84 + 506) * mapa_x, 10, "green", "nabieznik"); //  -84x25
            drawTriangle((82.8 + 64) * mapa_x, (-69 + 506) * mapa_x, 10, "green", "port nabieznik"); // port nabieznik ->         69x82.8
            drawTriangle((2 + 64) * mapa_x, (-130 + 506) * mapa_x, 10, "green", "pomost Lesniczowka"); // pomost Lesniczowka        130x2
            drawTriangle((79 + 64) * mapa_x, (-188 + 506) * mapa_x, 10, "green", "Slip kolej END"); // Slip kolej END           188x79
            drawTriangle((926 + 64) * mapa_x, (1149 + 506) * mapa_x, 10, "green", "Wiata END jeziora");
        }
    } catch (e) {
        console.error("Błąd podczas inicjalizacji Konva:", e);
    }
})();
