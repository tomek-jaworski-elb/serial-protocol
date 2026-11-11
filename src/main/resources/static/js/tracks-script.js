// /tracks: form, validation, the A4 map and the Refresh/Clear flow; tracks-draw.js draws what comes back.
// Dates are wall-clock values in the server's display zone, held in local Date objects only so that
// Flatpickr can show them; the browser's own zone never enters a calculation.

const form = document.getElementById('tracksForm');
const container = document.querySelector('.canvas-container');
const SCALE_BY = 1.1;      // wheel step, as on /chart
const BUTTON_ZOOM = 1.3;   // +/- button step, as on /chart
const MAX_SCALE = 4;
const A4_RATIO = 297 / 210; // height / width of the portrait page
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
    return date.getFullYear() + '-' + two(date.getMonth() + 1) + '-' + two(date.getDate());
}

// "2026-11-11T14:03:05": the picker's wall clock as the server reads it, never converted through UTC.
function isoLocal(date) {
    return dayKey(date) + 'T' + two(date.getHours()) + ':' + two(date.getMinutes()) + ':' + two(date.getSeconds());
}

// a picker's wall clock in the units of tracks-draw.js
function pickerWallMs(date) {
    return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes(), date.getSeconds());
}

const fromInput = document.getElementById('fromInput');
const toInput = document.getElementById('toInput');
const rangeError = document.getElementById('rangeError');
const refreshBtn = document.getElementById('refreshBtn');
let loading = false;
let fromPicker = null;
let toPicker = null;

// First problem with the chosen range, or '' when it can be queried.
function rangeProblem() {
    const from = fromPicker.selectedDates[0];
    const to = toPicker.selectedDates[0];
    if (!from || !to) return 'Choose both From and To.';
    if (from >= to) return 'From must be earlier than To.';
    if (to - from > DAY_MS) return 'The range cannot be longer than 24 hours.';
    if (from > wallClockNow()) return 'The range lies entirely in the future.';
    return '';
}

function validateRange() {
    const problem = rangeProblem();
    rangeError.textContent = problem;
    fromInput.classList.toggle('is-invalid', problem !== '');
    toInput.classList.toggle('is-invalid', problem !== '');
    refreshBtn.disabled = loading || problem !== '';
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

// ---------------------------------------------------------------- settings

const periodSlider = document.getElementById('periodSlider');
let chosenPeriod = Number(periodSlider.value); // the user's own choice; the range may raise the slider above it
const periodValue = document.getElementById('periodValue');
const showSilhouettes = document.getElementById('showSilhouettes');
const timeLabels = document.getElementById('timeLabels');
const gapInput = document.getElementById('gapInput');
const clearBtn = document.getElementById('clearBtn');
const printBtn = document.getElementById('printBtn');
const shipBoxes = [...form.querySelectorAll('input[name="ship"]')];
const SETTINGS_KEY = 'tracks.settings';
const MIN_TIME_LABEL_PERIOD = 10; // below this the labels would overlap

// The range, frame and view always start fresh; only these are remembered.
function loadSettings() {
    try {
        return JSON.parse(localStorage.getItem(SETTINGS_KEY)) ?? {};
    } catch (e) {
        return {};
    }
}

function saveSettings() {
    try {
        localStorage.setItem(SETTINGS_KEY, JSON.stringify({
            ships: shipBoxes.filter((box) => box.checked).map((box) => Number(box.value)),
            period: chosenPeriod, showSilhouettes: showSilhouettes.checked, timeLabels: timeLabels.checked, gap: gapInput.value
        }));
    } catch (e) {
        // storage blocked: the page works with its defaults
    }
}

function restoreSettings() {
    const saved = loadSettings();
    if (Array.isArray(saved.ships)) shipBoxes.forEach((box) => box.checked = saved.ships.includes(Number(box.value)));
    if (Number.isInteger(saved.period) && saved.period >= 0 && saved.period <= MAX_PERIOD_S) periodSlider.value = saved.period;
    chosenPeriod = Number(periodSlider.value);
    if (typeof saved.showSilhouettes === 'boolean') showSilhouettes.checked = saved.showSilhouettes;
    if (typeof saved.timeLabels === 'boolean') timeLabels.checked = saved.timeLabels;
    if (Number.isInteger(+saved.gap) && saved.gap >= 1 && saved.gap <= 600) gapInput.value = saved.gap;
}

// the controls are the source of truth; drawSettings is what tracks-draw.js reads
function readSettings() {
    const period = Number(periodSlider.value);
    periodValue.textContent = period;
    timeLabels.disabled = period < MIN_TIME_LABEL_PERIOD;
    drawSettings.period = period;
    drawSettings.showSilhouettes = showSilhouettes.checked;
    drawSettings.timeLabels = timeLabels.checked && !timeLabels.disabled;
}

// ---------------------------------------------------------------- messages

const messageBox = document.getElementById('tracksMessage');
let resultMessage = '';   // what the last Refresh found, or why it failed
let resultKind = 'info';
let periodNote = '';      // silhouette cap, about the drawing on the map

function renderMessage() {
    const text = [resultMessage, periodNote].filter(Boolean).join('\n');
    messageBox.textContent = text;
    messageBox.classList.toggle('d-none', text === '');
    for (const kind of ['info', 'warning', 'danger']) messageBox.classList.toggle('alert-' + kind, kind === resultKind);
}

function setResultMessage(text, kind = 'info') {
    resultMessage = text;
    resultKind = kind;
    renderMessage();
}

function setShipNotes(missingIds) {
    document.querySelectorAll('[data-ship-note]').forEach((note) => {
        note.textContent = missingIds.has(Number(note.dataset.shipNote)) ? 'no data' : '';
    });
}

// "No ... for this range" texts from the server's coverage
function emptyMessage(coverage, from, to) {
    if (coverage.logsFound) {
        const withDate = dayKey(from) !== dayKey(to);
        const text = (date) => formatWall(pickerWallMs(date), withDate);
        return `No positions for the selected ships between ${text(from)} and ${text(to)}. Logs for this period exist.`;
    }
    if (!coverage.historyFrom) return 'No logs have been recorded yet.';
    const day = (iso) => iso.split('-').reverse().join('/');
    return `No logs for this period. Recorded history: ${day(coverage.historyFrom)} – ${day(coverage.historyTo)}.`;
}

// "Data found ..." when the points cover only part of the range (more than a gap short at either end)
function partialMessage(coverage, from, to) {
    const first = wallMs(coverage.firstPoint);
    const last = wallMs(coverage.lastPoint);
    if (first - pickerWallMs(from) <= drawSettings.gapMs && pickerWallMs(to) - last <= drawSettings.gapMs) return '';
    const withDate = dayKey(from) !== dayKey(to);
    return `Data found ${formatWall(first, withDate)} – ${formatWall(last, withDate)}`;
}

// ---------------------------------------------------------------- drawing

let drawn = false; // at least one point is on the map

function updateButtons() {
    validateRange();
    clearBtn.disabled = loading || (!drawn && resultMessage === '');
    printBtn.disabled = loading || !drawn;
}

function setLoading(on) {
    loading = on;
    refreshBtn.querySelector('.spinner-border').classList.toggle('d-none', !on);
    updateButtons();
}

// Raise the slider's minimum when the period would give more than the silhouette cap.
function applyPeriodLimit() {
    const {min, fits} = periodLimit();
    periodSlider.min = min;
    periodNote = '';
    if (!fits) {
        periodSlider.value = MAX_PERIOD_S;
        periodNote = `Silhouettes shown every ${MAX_PERIOD_S} s: the range is too long for a shorter period`;
    } else {
        periodSlider.value = Math.max(chosenPeriod, min);
        if (chosenPeriod < min) periodNote = `Period raised to ${min} s: too many silhouettes for this range`;
    }
    readSettings();
    renderMessage();
}

function clearDrawing() {
    setTracks([], null);
    applyPeriodLimit();
    scheduleRedraw(REBUILD_TRACKS);
    drawn = false;
    setShipNotes(new Set());
}

function showResult(result, requestedIds, from, to) {
    const withPoints = result.ships.filter((ship) => ship.points.length);
    if (withPoints.length === 0) {
        clearDrawing();
        setShipNotes(new Set(requestedIds));
        setResultMessage(emptyMessage(result.coverage, from, to), 'warning');
        return;
    }
    const colorOf = (id) => shipBoxes.find((box) => Number(box.value) === id).dataset.color;
    setTracks(withPoints, colorOf);
    applyPeriodLimit();
    scheduleRedraw(REBUILD_TRACKS);
    drawn = true;
    drawnFrom = from;
    drawnTo = to;
    setShipNotes(new Set(requestedIds.filter((id) => !withPoints.some((ship) => ship.id === id))));
    setResultMessage(partialMessage(result.coverage, from, to));
}

async function refresh() {
    const ids = shipBoxes.filter((box) => box.checked).map((box) => Number(box.value));
    if (ids.length === 0) {
        setResultMessage('Choose at least one ship.', 'warning');
        updateButtons();
        return;
    }
    const from = fromPicker.selectedDates[0];
    const to = toPicker.selectedDates[0];
    setLoading(true);
    try {
        const response = await fetch(`/tracks/api/points?from=${isoLocal(from)}&to=${isoLocal(to)}&models=${ids.join(',')}`);
        if (response.ok) {
            showResult(await response.json(), ids, from, to);
        } else if (response.status === 503) {
            setResultMessage('The server is busy with another track query, try again in a moment.', 'danger');
        } else {
            const body = await response.json().catch(() => ({}));
            setResultMessage(body.message || 'The track query failed.', 'danger');
        }
    } catch (e) {
        console.error('Track query failed:', e);
        setResultMessage('The track query failed.', 'danger');
    } finally {
        setLoading(false);
    }
}

function clear() {
    clearDrawing();
    setResultMessage('');
    updateButtons();
}

// ---------------------------------------------------------------- print

const PRINT_DPI = 300;
const JPEG_QUALITY = 0.92; // keeps a full A4 image at 2-3 MB, under the 10 MB upload limit
// On paper, in millimetres
const PRINT_MM = {line: 0.3, dotRadius: 0.4, outline: 0.3, labelFont: 2, labelGap: 0.75};
let drawnFrom = null; // the range of the drawing on the map, which is what gets printed
let drawnTo = null;

// Renders the print area at PRINT_DPI into a new canvas, fitted into the PDF's image box (the server
// owns that size, the page gets it from the form). The stage is drawn once at scale 1 and the canvas
// scaled by pixelRatio, so the map units are the units of the sizes; the stage is put back before
// anything can repaint, and the layers on the page are not touched.
function renderPrintArea(area) {
    const boxWidthMm = Number(form.dataset.boxWidthMm);
    const boxHeightMm = Number(form.dataset.boxHeightMm);
    const mmPerUnit = Math.min(boxWidthMm / area.width, boxHeightMm / area.height);
    const mapUnits = Object.fromEntries(Object.entries(PRINT_MM).map(([name, mm]) => [name, mm / mmPerUnit]));
    const scale = konvaStage.scale();
    const position = konvaStage.position();
    printLayer.visible(false);
    konvaStage.scale({x: 1, y: 1});
    konvaStage.position({x: 0, y: 0});
    try {
        const canvas = drawWithSizes(mapUnits, () => konvaStage.toCanvas({...area, pixelRatio: mmPerUnit * PRINT_DPI / 25.4}));
        // JPEG has no transparency: where the area reaches past the map it would turn black
        const context = canvas.getContext('2d');
        context.setTransform(1, 0, 0, 1, 0, 0); // Konva leaves its export offset on the context
        context.globalCompositeOperation = 'destination-over';
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
        return canvas;
    } finally {
        konvaStage.scale(scale);
        konvaStage.position(position);
        printLayer.visible(true);
    }
}

async function printTracks() {
    // Opened in the click itself: the JPEG below is made asynchronously, and Safari and Firefox
    // treat a window opened after that as an unrequested popup.
    const pdfWindow = window.open('', document.getElementById('pdfForm').target);
    if (!pdfWindow) {
        setResultMessage('The browser blocked the PDF window. Allow pop-ups for this site and press Print again.', 'warning');
        return;
    }
    const area = getPrintArea();
    printBtn.disabled = true;
    try {
        const blob = await new Promise((resolve) => renderPrintArea(area).toBlob(resolve, 'image/jpeg', JPEG_QUALITY));
        const pdfForm = document.getElementById('pdfForm');
        const file = new DataTransfer();
        file.items.add(new File([blob], 'tracks.jpg', {type: 'image/jpeg'}));
        pdfForm.elements.image.files = file.files;
        pdfForm.elements.from.value = isoLocal(drawnFrom);
        pdfForm.elements.to.value = isoLocal(drawnTo);
        pdfForm.elements.silhouettes.value = drawSettings.showSilhouettes ? drawSettings.period : -1;
        pdfForm.elements.ships.value = tracks.map((track) => track.id).join(',');
        pdfForm.elements.widthMeters.value = area.width / mapa_x;
        pdfForm.submit();
    } catch (e) {
        pdfWindow.close();
        throw e;
    } finally {
        updateButtons();
    }
}

function bindControls() {
    refreshBtn.addEventListener('click', refresh);
    clearBtn.addEventListener('click', clear);
    printBtn.addEventListener('click', printTracks);
    shipBoxes.forEach((box) => box.addEventListener('change', saveSettings));
    periodSlider.addEventListener('input', () => {
        chosenPeriod = Number(periodSlider.value);
        readSettings();
        saveSettings();
        scheduleRedraw(REBUILD_SILHOUETTES);
    });
    for (const toggle of [showSilhouettes, timeLabels]) {
        toggle.addEventListener('change', () => {
            readSettings();
            saveSettings();
            scheduleRedraw(REPAINT);
        });
    }
    // 'change', not 'input': re-segmenting a 24 h track per keystroke would stutter
    gapInput.addEventListener('change', () => {
        const gap = Math.min(600, Math.max(1, Math.round(Number(gapInput.value)) || 1));
        gapInput.value = gap;
        setGap(gap);
        applyPeriodLimit();
        saveSettings();
        scheduleRedraw(REBUILD_TRACKS);
    });
}

// ---------------------------------------------------------------- init

function initStage() {
    // small mouse jitter on a click must not start a pan
    Konva.dragDistance = 3;
    konvaStage = new Konva.Stage({container: 'konvaContainer', width: container.clientWidth, height: container.clientHeight, draggable: true});
    konvaStage.dragBoundFunc((pos) => clampStagePosition(pos, konvaStage.scaleX()));

    // layers, bottom to top; trackLayer and silhouetteLayer are filled by tracks-draw.js
    const mapLayer = new Konva.Layer({listening: false});
    trackLayer = new Konva.Layer({listening: false});
    silhouetteLayer = new Konva.Layer({listening: false});
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
restoreSettings();
readSettings();
setGap(Number(gapInput.value));
bindControls();

let resizeTimeout = null;
window.addEventListener('resize', () => {
    clearTimeout(resizeTimeout);
    resizeTimeout = setTimeout(() => fitStageToContainer(false), 100);
});
