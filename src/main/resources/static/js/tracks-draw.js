// /tracks: turns the fetched points into Konva shapes (lines, dots, silhouettes).
// Positions are converted to map coordinates once per fetch; segments and silhouettes are rebuilt only
// when the gap or the period change. Each ship's track and silhouettes are one Konva.Shape with a
// sceneFunc, because thousands of nodes (or a Konva.Line of 86 400 points) would be too slow.
// Hulls are sized in map units, never from the live zoom.

const LINE_WIDTH_PX = 1.5;          // lines, dots and outlines keep these sizes on screen at any zoom
const DOT_RADIUS_PX = 2;
const OUTLINE_WIDTH_PX = 1.2;
const MIN_VERTEX_SPACING_PX = 1;    // a line vertex closer than this to the last drawn one is skipped
const MIN_DOT_SPACING_PX = 2;       // dots (4 px across) still overlap, so thinning does not show
const LABEL_FONT = '11px sans-serif';
const LABEL_COLOR = '#151a22';
const SILHOUETTE_FILL_ALPHA = 0.25;
const SILHOUETTE_CAP = 5000;
const MAX_PERIOD_S = 60;
const MAX_TOLERANCE_MS = 2000;
const DAY_MS = 24 * 3600 * 1000;
const ZONE_BUCKET_MS = 15 * 60 * 1000; // UTC offsets only change on a quarter-hour boundary

// Set by the page; the layers are the Konva layers to fill.
const drawSettings = {gapMs: 10000, period: 10, showSilhouettes: true, timeLabels: false};
let trackLayer = null;
let silhouetteLayer = null;
// Per ship: {id, color, n, t (epoch ms), xy (map x,y interleaved), heading, starts (first index of each segment)}
let tracks = [];

// ---------------------------------------------------------------- display-zone clock

const zoneFormat = new Intl.DateTimeFormat('en-CA', {
    timeZone: document.getElementById('tracksForm').dataset.zone, hourCycle: 'h23',
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit'
});
const zoneOffsets = new Map();

// UTC offset of the display zone at epoch time t (never the browser's zone).
function zoneOffsetMs(t) {
    const bucket = Math.floor(t / ZONE_BUCKET_MS);
    let offset = zoneOffsets.get(bucket);
    if (offset === undefined) {
        const parts = Object.fromEntries(zoneFormat.formatToParts(bucket * ZONE_BUCKET_MS).map((p) => [p.type, +p.value]));
        offset = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second) - bucket * ZONE_BUCKET_MS;
        zoneOffsets.set(bucket, offset);
    }
    return offset;
}

// Wall-clock milliseconds of the display zone, held as if they were UTC.
function wallMs(t) {
    return t + zoneOffsetMs(t);
}

const two = (n) => String(n).padStart(2, '0');

// "HH:mm:ss" of a wall-clock value; with the date as "dd/MM/yyyy HH:mm:ss".
function formatWall(wall, withDate) {
    const d = new Date(wall);
    const time = two(d.getUTCHours()) + ':' + two(d.getUTCMinutes()) + ':' + two(d.getUTCSeconds());
    return withDate ? two(d.getUTCDate()) + '/' + two(d.getUTCMonth() + 1) + '/' + d.getUTCFullYear() + ' ' + time : time;
}

// ---------------------------------------------------------------- data

function segmentEnd(track, s) {
    return track.starts[s + 1] ?? track.n;
}

// a gap longer than the threshold starts a new segment
function segment() {
    for (const track of tracks) {
        track.starts = [0];
        for (let i = 1; i < track.n; i++) {
            if (track.t[i] - track.t[i - 1] > drawSettings.gapMs) track.starts.push(i);
        }
    }
}

// ships: [{id, points: [[t, x, y, heading], ...]}] from the server; colorOf(id) gives the ship colour
function setTracks(ships, colorOf) {
    tracks = ships.filter((ship) => ship.points.length).map(({id, points}) => {
        const n = points.length;
        const track = {id, color: colorOf(id), n, t: new Float64Array(n), xy: new Float64Array(2 * n), heading: new Float64Array(n), starts: []};
        points.forEach(([time, x, y, heading], i) => {
            const p = getScaledPoints(x, y);
            track.t[i] = time;
            track.xy[2 * i] = p.x;
            track.xy[2 * i + 1] = p.y;
            track.heading[i] = heading;
        });
        return track;
    });
    segment();
}

function setGap(seconds) {
    drawSettings.gapMs = seconds * 1000;
    segment();
}

// ---------------------------------------------------------------- silhouette instants

// Smallest wall-clock instant >= wall that is a multiple of stepMs since the local midnight.
function nextAligned(wall, stepMs) {
    const midnight = Math.floor(wall / DAY_MS) * DAY_MS;
    return Math.min(midnight + Math.ceil((wall - midnight) / stepMs) * stepMs, midnight + DAY_MS);
}

// Indices of the points that get a silhouette in one ship; null as soon as there would be more than budget.
// Period 0: every point. Otherwise the nearest point to each clock-aligned instant (if close enough), plus
// the first and last point of every segment. Indices ascend, so a point is never taken twice.
function pickSilhouettes(track, periodS, budget) {
    const picked = [];
    const stepMs = periodS * 1000;
    const tolerance = Math.min(stepMs / 2, MAX_TOLERANCE_MS);
    for (let s = 0; s < track.starts.length; s++) {
        const first = track.starts[s];
        const last = segmentEnd(track, s) - 1;
        if (periodS === 0) {
            if (picked.length + last - first + 1 > budget) return null;
            for (let i = first; i <= last; i++) picked.push(i);
            continue;
        }
        picked.push(first);
        let instant = track.t[first];
        let wall = nextAligned(wallMs(instant), stepMs);
        let near = first; // moves forward only
        for (;;) {
            instant = wall - zoneOffsetMs(instant); // offset of the previous instant: stays increasing across DST changes
            if (instant > track.t[last]) break;
            while (near < last && track.t[near + 1] <= instant) near++;
            const nearest = near < last && track.t[near + 1] - instant < instant - track.t[near] ? near + 1 : near;
            if (Math.abs(track.t[nearest] - instant) <= tolerance && nearest > picked[picked.length - 1]) picked.push(nearest);
            if (picked.length > budget) return null;
            wall = nextAligned(wallMs(instant) + 1, stepMs);
        }
        if (picked[picked.length - 1] < last) picked.push(last);
        if (picked.length > budget) return null;
    }
    return picked;
}

// One index list per ship, or null when the whole drawing would exceed the budget.
function pickAll(periodS, budget) {
    const all = [];
    for (const track of tracks) {
        const picked = pickSilhouettes(track, periodS, budget);
        if (!picked) return null;
        budget -= picked.length;
        all.push(picked);
    }
    return all;
}

// The smallest period that keeps the drawing within SILHOUETTE_CAP; fits is false when even 60 s does not.
function periodLimit() {
    for (let period = 0; period <= MAX_PERIOD_S; period++) {
        if (pickAll(period, SILHOUETTE_CAP)) return {min: period, fits: true};
    }
    return {min: MAX_PERIOD_S, fits: false};
}

// ---------------------------------------------------------------- shapes

// Map-coordinate rectangle the canvas being drawn shows (also right when printing a part of the stage).
function visibleRect(ctx, margin) {
    const native = ctx._context;
    const inverse = native.getTransform().inverse();
    const a = inverse.transformPoint(new DOMPoint(0, 0));
    const b = inverse.transformPoint(new DOMPoint(native.canvas.width, native.canvas.height));
    return {x0: Math.min(a.x, b.x) - margin, x1: Math.max(a.x, b.x) + margin, y0: Math.min(a.y, b.y) - margin, y1: Math.max(a.y, b.y) + margin};
}

// A ship's lines and dots in one shape. Both are thinned in screen space (a vertex or dot within
// the minimum spacing of the last drawn one is skipped), which is invisible but keeps 24 h of 1 Hz data
// cheap at low zoom; the data is untouched and at full zoom every point is drawn.
function trackSceneFunc(track) {
    return (ctx, shape) => {
        const scale = shape.getAbsoluteScale().x;
        const vertexSpacing = MIN_VERTEX_SPACING_PX / scale;
        const dotSpacing = MIN_DOT_SPACING_PX / scale;
        const radius = DOT_RADIUS_PX / scale;
        const xy = track.xy;
        ctx.beginPath();
        track.starts.forEach((first, s) => {
            const last = segmentEnd(track, s) - 1;
            if (last === first) return;
            let lastX = xy[2 * first];
            let lastY = xy[2 * first + 1];
            ctx.moveTo(lastX, lastY);
            for (let i = first + 1; i < last; i++) {
                const x = xy[2 * i];
                const y = xy[2 * i + 1];
                if (Math.abs(x - lastX) < vertexSpacing && Math.abs(y - lastY) < vertexSpacing) continue;
                ctx.lineTo(x, y);
                lastX = x;
                lastY = y;
            }
            ctx.lineTo(xy[2 * last], xy[2 * last + 1]); // the end stays exact
        });
        ctx.strokeShape(shape);

        const view = visibleRect(ctx, radius);
        let lastX = -1e9;
        let lastY = -1e9;
        ctx.beginPath();
        for (let i = 0; i < xy.length; i += 2) {
            const x = xy[i];
            const y = xy[i + 1];
            if (x < view.x0 || x > view.x1 || y < view.y0 || y > view.y1) continue;
            if (Math.abs(x - lastX) < dotSpacing && Math.abs(y - lastY) < dotSpacing) continue;
            ctx.moveTo(x + radius, y);
            ctx.arc(x, y, radius, 0, 2 * Math.PI);
            lastX = x;
            lastY = y;
        }
        ctx.fillShape(shape);
    };
}

// The hulls of one ship, built once as a Path2D in map units: a path of thousands of subpaths is far
// cheaper to replay than to rebuild at every pan and zoom step. Outline width and labels are counter-scaled.
function silhouettesSceneFunc(track, indices, outline, halfLength) {
    const {r, g, b} = Konva.Util.getRGB(track.color);
    const fill = `rgba(${r}, ${g}, ${b}, ${SILHOUETTE_FILL_ALPHA})`;
    return (ctx, shape) => {
        const native = ctx._context;
        const scale = shape.getAbsoluteScale().x;
        native.fillStyle = fill;
        native.fill(outline);
        native.strokeStyle = track.color;
        native.lineWidth = OUTLINE_WIDTH_PX / scale;
        native.stroke(outline);
        if (!drawSettings.timeLabels) return;
        const view = visibleRect(ctx, halfLength);
        native.font = LABEL_FONT;
        native.fillStyle = LABEL_COLOR;
        native.textBaseline = 'middle';
        for (const i of indices) {
            const x = track.xy[2 * i];
            const y = track.xy[2 * i + 1];
            if (x < view.x0 || x > view.x1 || y < view.y0 || y > view.y1) continue;
            native.save();
            native.translate(x, y);
            native.scale(1 / scale, 1 / scale); // text keeps its size on screen
            native.fillText(formatWall(wallMs(track.t[i])), halfLength * scale + 3, 0);
            native.restore();
        }
    };
}

function rebuildSilhouettes() {
    silhouetteLayer.destroyChildren();
    const picked = pickAll(drawSettings.period, Infinity);
    tracks.forEach((track, k) => {
        const hull = SHIP_HULLS[track.id];
        const outline = new Path2D();
        for (const i of picked[k]) {
            const points = computeShipVerticesForKonva(track.xy[2 * i], track.xy[2 * i + 1], hull.scale, track.heading[i], ...hull.shipParams);
            outline.moveTo(points[0], points[1]);
            for (let j = 2; j < points.length; j += 2) outline.lineTo(points[j], points[j + 1]);
            outline.closePath();
        }
        silhouetteLayer.add(new Konva.Shape({
            sceneFunc: silhouettesSceneFunc(track, picked[k], outline, hull.shipParams[0] * hull.scale / 2), listening: false
        }));
    });
}

function rebuildTracks() {
    trackLayer.destroyChildren();
    for (const track of tracks) {
        trackLayer.add(new Konva.Shape({
            sceneFunc: trackSceneFunc(track), fill: track.color, stroke: track.color, strokeWidth: LINE_WIDTH_PX,
            strokeScaleEnabled: false, listening: false, perfectDrawEnabled: false
        }));
    }
}

// ---------------------------------------------------------------- redraw

// What a change needs, cheapest first: only repaint the silhouettes (toggles), rebuild them (period),
// or rebuild everything (new data, gap).
const REPAINT = 0;
const REBUILD_SILHOUETTES = 1;
const REBUILD_TRACKS = 2;
let pendingLevel = -1;
let frame = 0;

// Changes within one frame are coalesced into a single redraw.
function scheduleRedraw(level) {
    pendingLevel = Math.max(pendingLevel, level);
    if (!frame) frame = requestAnimationFrame(redraw);
}

function redraw() {
    if (pendingLevel === REBUILD_TRACKS) rebuildTracks();
    if (pendingLevel >= REBUILD_SILHOUETTES) rebuildSilhouettes();
    if (pendingLevel === REBUILD_TRACKS) trackLayer.draw();
    silhouetteLayer.visible(drawSettings.showSilhouettes);
    silhouetteLayer.draw();
    pendingLevel = -1;
    frame = 0;
}
