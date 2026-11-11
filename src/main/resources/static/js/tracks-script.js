// /tracks: form, validation and the A4 map. Drawing the tracks is not wired in yet.
// Dates are wall-clock values in the server's display zone, held in local Date objects only so that
// Flatpickr can show them; the browser's own zone never enters a calculation.

const form = document.getElementById('tracksForm');
const container = document.querySelector('.canvas-container');
const SCALE_BY = 1.1;      // wheel step, as on /chart
const BUTTON_ZOOM = 1.3;   // +/- button step, as on /chart
const MAX_SCALE = 4;
const A4_RATIO = 297 / 210; // height / width of the portrait page
const MAX_RANGE_MS = 24 * 3600 * 1000;
const SCALE_BAR_MAX_PX = 120;

// ---------------------------------------------------------------- time range

// "2026-11-11T14:03:05" (server clock, display zone) as a wall-clock Date.
function parseWallClock(text) {
    const [y, mo, d, h, mi, s] = text.split(/\D/).map(Number);
    return new Date(y, mo - 1, d, h, mi, s);
}

// The server's clock stays authoritative: only the time elapsed in this tab is added to it.
const wallClockOffsetMs = parseWallClock(form.dataset.now).getTime() - Date.now();
function wallClockNow() {
    return new Date(Date.now() + wallClockOffsetMs);
}

function dayKey(date) {
    const two = (n) => String(n).padStart(2, '0');
    return date.getFullYear() + '-' + two(date.getMonth() + 1) + '-' + two(date.getDate());
}

const fromInput = document.getElementById('fromInput');
const toInput = document.getElementById('toInput');
const rangeError = document.getElementById('rangeError');
const refreshBtn = document.getElementById('refreshBtn');
let fromPicker = null;
let toPicker = null;

// First problem with the chosen range, or '' when it can be queried.
function rangeProblem() {
    const from = fromPicker.selectedDates[0];
    const to = toPicker.selectedDates[0];
    if (!from || !to) return 'Choose both From and To.';
    if (from >= to) return 'From must be earlier than To.';
    if (to - from > MAX_RANGE_MS) return 'The range cannot be longer than 24 hours.';
    if (from > wallClockNow()) return 'The range lies entirely in the future.';
    return '';
}

function validateRange() {
    const problem = rangeProblem();
    rangeError.textContent = problem;
    fromInput.classList.toggle('is-invalid', problem !== '');
    toInput.classList.toggle('is-invalid', problem !== '');
    refreshBtn.disabled = problem !== '';
}

async function initPickers() {
    let days = [];
    try {
        const response = await fetch('/tracks/api/days');
        days = (await response.json()).days;
    } catch (e) {
        console.error('Recorded days unavailable:', e);
    }
    const now = wallClockNow();
    const defaultFrom = new Date(now.getTime() - 3600 * 1000);
    // the default range stays selectable even before its day has a log (just after midnight)
    const recordedDays = new Set([...days, dayKey(now), dayKey(defaultFrom)]);
    const config = {
        enableTime: true,
        enableSeconds: true,
        time_24hr: true,
        dateFormat: 'd/m/Y H:i:S',
        allowInput: true,
        locale: {firstDayOfWeek: 1},
        enable: [(date) => recordedDays.has(dayKey(date))],
        onChange: validateRange
    };
    fromPicker = flatpickr(fromInput, {...config, defaultDate: defaultFrom});
    toPicker = flatpickr(toInput, {...config, defaultDate: now});
    validateRange();
}

// ---------------------------------------------------------------- map

const SCALE_BAR_STEPS = [1, 2, 5];
let konvaStage = null;
let minScale = 0.1; // recomputed on resize: the whole map fits

function clampScale(scale) {
    return Math.max(minScale, Math.min(MAX_SCALE, scale));
}

// keep the map covering the viewport; centre it when smaller than the viewport
function clampStagePosition(pos, scale) {
    const mapW = MAP_WIDTH * scale;
    const mapH = MAP_HEIGHT * scale;
    const x = mapW <= konvaStage.width() ? (konvaStage.width() - mapW) / 2 : Math.min(0, Math.max(konvaStage.width() - mapW, pos.x));
    const y = mapH <= konvaStage.height() ? (konvaStage.height() - mapH) / 2 : Math.min(0, Math.max(konvaStage.height() - mapH, pos.y));
    return {x, y};
}

// metric scale bar: a round length (1/2/5 x 10^n m) at most SCALE_BAR_MAX_PX wide
function updateScaleBar() {
    const metresPerPixel = 1 / (mapa_x * konvaStage.scaleX());
    const limit = SCALE_BAR_MAX_PX * metresPerPixel;
    const decade = Math.pow(10, Math.floor(Math.log10(limit)));
    const metres = decade * SCALE_BAR_STEPS.findLast((step) => step * decade <= limit);
    document.getElementById('scaleBarLine').style.width = (metres / metresPerPixel) + 'px';
    document.getElementById('scaleBarLabel').textContent = metres >= 1000 ? (metres / 1000) + ' km' : metres + ' m';
}

function setView(scale, pos) {
    konvaStage.scale({x: scale, y: scale});
    konvaStage.position(clampStagePosition(pos, scale));
    updateScaleBar();
}

// resize to the container; resetView fits the whole map
function fitStageToContainer(resetView) {
    konvaStage.width(container.clientWidth);
    konvaStage.height(container.clientHeight);
    minScale = Math.min(konvaStage.width() / MAP_WIDTH, konvaStage.height() / MAP_HEIGHT);
    setView(resetView ? minScale : clampScale(konvaStage.scaleX()), konvaStage.position());
}

// zoom keeping the given viewport point fixed
function zoomAt(pointer, newScaleRaw) {
    const oldScale = konvaStage.scaleX();
    const newScale = clampScale(newScaleRaw);
    const mapPoint = {x: (pointer.x - konvaStage.x()) / oldScale, y: (pointer.y - konvaStage.y()) / oldScale};
    setView(newScale, {x: pointer.x - mapPoint.x * newScale, y: pointer.y - mapPoint.y * newScale});
}

// pointer in map coordinates
function mapPointer() {
    const pointer = konvaStage.getPointerPosition();
    const scale = konvaStage.scaleX();
    return {x: (pointer.x - konvaStage.x()) / scale, y: (pointer.y - konvaStage.y()) / scale};
}

function bindMapControls() {
    const viewportCentre = () => ({x: konvaStage.width() / 2, y: konvaStage.height() / 2});
    document.getElementById('zoomInBtn').addEventListener('click', () => zoomAt(viewportCentre(), konvaStage.scaleX() * BUTTON_ZOOM));
    document.getElementById('zoomOutBtn').addEventListener('click', () => zoomAt(viewportCentre(), konvaStage.scaleX() / BUTTON_ZOOM));
    document.getElementById('zoomResetBtn').addEventListener('click', () => fitStageToContainer(true));
    konvaStage.on('wheel', (e) => {
        e.evt.preventDefault();
        zoomAt(konvaStage.getPointerPosition(), e.evt.deltaY > 0 ? konvaStage.scaleX() / SCALE_BY : konvaStage.scaleX() * SCALE_BY);
    });
}

// ---------------------------------------------------------------- print frame

// Layers, bottom to top. Tracks and silhouettes stay empty until the drawing is wired in.
let printLayer = null;
let printFrame = null;       // Konva.Rect in map coordinates, or null
let frameTransformer = null;
let drawingFrom = null;      // map point where the current frame drag began
let frameStarted = false;    // the drag has become a real frame
let frameMode = false;
const printFrameBtn = document.getElementById('printFrameBtn');
const removeFrameBtn = document.getElementById('removeFrameBtn');

function setFrameMode(on) {
    frameMode = on;
    printFrameBtn.setAttribute('aria-pressed', on);
    printFrameBtn.classList.toggle('active', on);
    konvaStage.draggable(!on);
    // while drawing, an existing frame must not catch the drag
    printLayer.listening(!on);
    container.style.cursor = on ? 'crosshair' : '';
}

function removeFrame() {
    printFrame?.destroy();
    frameTransformer.nodes([]);
    printFrame = null;
    removeFrameBtn.disabled = true;
}

// A4-portrait rectangle from the drag start to the pointer; the larger of the two drags decides the size.
function setFrameRect(corner) {
    const width = Math.max(Math.abs(corner.x - drawingFrom.x), Math.abs(corner.y - drawingFrom.y) / A4_RATIO);
    printFrame.setAttrs({
        x: corner.x < drawingFrom.x ? drawingFrom.x - width : drawingFrom.x,
        y: corner.y < drawingFrom.y ? drawingFrom.y - width * A4_RATIO : drawingFrom.y,
        width,
        height: width * A4_RATIO
    });
}

function startFrameDrag() {
    if (!frameMode) return;
    drawingFrom = mapPointer();
    frameStarted = false;
}

function moveFrameDrag() {
    if (!drawingFrom) return;
    const corner = mapPointer();
    if (!frameStarted) {
        // a click is not a frame: wait for a real drag, then replace any earlier frame
        if (Math.hypot(corner.x - drawingFrom.x, corner.y - drawingFrom.y) * konvaStage.scaleX() < 5) return;
        frameStarted = true;
        removeFrame();
        printFrame = new Konva.Rect({
            fill: 'rgba(47, 125, 224, 0.08)', stroke: '#2f7de0', strokeWidth: 2, strokeScaleEnabled: false, draggable: true
        });
        // Transformer scales the rect; fold that back into width/height so the rect stays plain
        printFrame.on('transformend', () => {
            printFrame.setAttrs({
                width: printFrame.width() * printFrame.scaleX(),
                height: printFrame.height() * printFrame.scaleY(),
                scaleX: 1, scaleY: 1
            });
        });
        printLayer.add(printFrame);
        printFrame.moveToBottom(); // the Transformer's handles must stay on top of it
    }
    setFrameRect(corner);
}

function endFrameDrag() {
    if (!drawingFrom) return;
    drawingFrom = null;
    if (!frameStarted) return; // only a click: keep waiting for a drag
    frameTransformer.nodes([printFrame]);
    removeFrameBtn.disabled = false;
    setFrameMode(false);
}

function bindPrintFrame() {
    printFrameBtn.addEventListener('click', () => setFrameMode(!frameMode));
    removeFrameBtn.addEventListener('click', removeFrame);
    konvaStage.on('mousedown touchstart', startFrameDrag);
    konvaStage.on('mousemove touchmove', moveFrameDrag);
    window.addEventListener('mouseup', endFrameDrag);
    window.addEventListener('touchend', endFrameDrag);
}

// The area to print, in map coordinates: the frame, or the current view when there is none.
function getPrintArea() {
    if (printFrame) {
        return {x: printFrame.x(), y: printFrame.y(), width: printFrame.width(), height: printFrame.height()};
    }
    const scale = konvaStage.scaleX();
    return {x: -konvaStage.x() / scale, y: -konvaStage.y() / scale, width: konvaStage.width() / scale, height: konvaStage.height() / scale};
}

// ---------------------------------------------------------------- init

function initStage() {
    // small mouse jitter on a click must not start a pan
    Konva.dragDistance = 3;
    konvaStage = new Konva.Stage({container: 'konvaContainer', width: container.clientWidth, height: container.clientHeight, draggable: true});
    konvaStage.dragBoundFunc((pos) => clampStagePosition(pos, konvaStage.scaleX()));

    const mapLayer = new Konva.Layer({listening: false});
    const trackLayer = new Konva.Layer({listening: false});
    const silhouetteLayer = new Konva.Layer({listening: false});
    printLayer = new Konva.Layer();
    konvaStage.add(mapLayer, trackLayer, silhouetteLayer, printLayer);

    frameTransformer = new Konva.Transformer({
        keepRatio: true,
        rotateEnabled: false,
        enabledAnchors: ['top-left', 'top-right', 'bottom-left', 'bottom-right'],
        // box is in screen pixels: refuse to shrink the frame to nothing
        boundBoxFunc: (oldBox, newBox) => newBox.width < 30 ? oldBox : newBox
    });
    printLayer.add(frameTransformer);

    loadMapImage(MAP_IMAGE_SOURCES)
        .then((img) => {
            mapLayer.add(new Konva.Image({image: img, x: 0, y: 0, width: MAP_WIDTH, height: MAP_HEIGHT}));
            mapLayer.batchDraw();
        })
        .catch((err) => console.error('Map image loading failed:', err));

    fitStageToContainer(true);
    bindMapControls();
    bindPrintFrame();
}

initStage();
initPickers();

let resizeTimeout = null;
window.addEventListener('resize', () => {
    clearTimeout(resizeTimeout);
    resizeTimeout = setTimeout(() => fitStageToContainer(false), 100);
});

document.getElementById('periodSlider').addEventListener('input', (e) => {
    document.getElementById('periodValue').textContent = e.target.value;
});
