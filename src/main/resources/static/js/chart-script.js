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
// Upper zoom limit. Follow mode needs far more than the flat 3 this used to be: at
// "ship length = 25% of viewport height" the shortest ship on a tall screen asks for
// about 12. Recomputed against the viewport in updateMaxScale(); 3 stays the floor,
// so the normal north-up view is never less zoomable than it was.
let maxScale = 3;
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
    // While following, the floor is the zoom at which the LOD crossfade is fully on the
    // hull (FOLLOW_MIN_SCALE) rather than "the whole chart fits": the mode promises a
    // silhouette, and below that threshold ships turn back into arrows. Wanting a wider
    // view means leaving the mode, which restores the ordinary floor.
    const floor = followedShipId !== null ? Math.max(minScale, FOLLOW_MIN_SCALE) : minScale;
    return Math.max(floor, Math.min(maxScale, scale));
}

// tap target size for ship shapes, in screen pixels; hitStrokeWidth is expressed
// in map units, so it must be rescaled on every zoom change to stay tappable
const SHIP_HIT_SCREEN_PX = 25;

// LOD arrow target size, in screen pixels — same "constant on-screen size" trick
// as SHIP_HIT_SCREEN_PX, recomputed on every zoom change (updateShipRenderMode)
const SHIP_ARROW_SCREEN_PX = 16;

// Line widths in SCREEN pixels, held constant at every zoom by strokeScaleEnabled:false.
// Both used to be map-unit widths, which made them vanish across the whole chart and swell
// to a ribbon close in.
const TRACK_STROKE_PX = 2;
const HULL_STROKE_PX = 1;

// crossfade band, in stage scale: below LOD_ARROW_SCALE = pure arrow, above
// LOD_HULL_SCALE = pure hull. Picked empirically against minScale/maxScale.
const LOD_ARROW_SCALE = 0.4;
const LOD_HULL_SCALE = 0.9;

// ------------------------------------------------------------------
// FOLLOW MODE — camera state
// ------------------------------------------------------------------
// Exactly one ship can be followed at a time; null means the mode is off. This is
// the single source of truth for the mode — the DOM only ever reflects it. Reading
// it back out of the markup is what silently broke the map tooltip's speed once the
// list was restyled, and the same trap is waiting for a pressed-state class.
let followedShipId = null;

// The rotation the camera is heading for, and the one it currently draws with.
// They are set together today; the smoothing step (lerp along the shorter way round
// plus a dead band) slots in between them without touching anything that reads
// followAngle.
let followTargetAngle = 0;
let followAngle = 0;

// Set the moment the user zooms by hand while following, so a later message — or a
// window resize — does not drag the zoom back to the optimum behind their back. Cleared
// when the mode engages or moves to another ship, since the optimum is per ship.
let followManualZoom = false;

// Zoom floor for the mode, borrowed from the LOD threshold on purpose rather than being
// a number of its own: the promise is "silhouette, not arrow", and LOD_HULL_SCALE is
// exactly where the crossfade finishes. Move that constant and this follows.
const FOLLOW_MIN_SCALE = LOD_HULL_SCALE;

// Heading changes below this are not worth turning the chart for: at roughly one
// message a second, sensor noise of a degree or two would otherwise rock the whole map
// back and forth forever.
const FOLLOW_ANGLE_DEAD_BAND = 1;

// Time to cover ~95% of the remaining distance to the target angle. The easing is
// exponential and framerate-independent, so this is a feel setting, not a frame count.
const FOLLOW_EASE_MS = 300;

// Once this close to the target the remainder is not worth another frame: snap and let
// the loop sleep. Must stay well under FOLLOW_ANGLE_DEAD_BAND or the two fight.
const FOLLOW_ANGLE_SETTLED = 0.05;

// ------------------------------------------------------------------
// SHIP POSITION SMOOTHING
// ------------------------------------------------------------------
// Messages arrive about once a second, so a hull drawn straight at each new position
// teleports. In the normal view that step is a few pixels and nobody sees it; in follow
// mode the ship is pinned to the anchor, so the same step is taken by the whole chart —
// about 20 px at a 2 kn manoeuvring speed and 120 px at a 12 kn transit.
//
// The cure is to slide each hull from its previous reported position to the new one.
// Every ship is eased, never just the followed one: a uniform delay leaves the distances
// and bearings between ships truthful, where easing one ship alone would misplace it
// against the others by up to a full message of travel.
//
// Time to cover ~95% of the distance. 0 disables smoothing entirely and restores the
// original teleporting behaviour — set from chart.ship.position-smoothing-ms.
const SHIP_POSITION_EASE_MS = typeof SHIP_POSITION_SMOOTHING_MS_VAR === 'number'
    ? SHIP_POSITION_SMOOTHING_MS_VAR
    : 300;

// Below this much remaining travel (in map units, so ~0.4 m) the rest is not worth a
// frame: snap and let the loop sleep.
const SHIP_POSITION_SETTLED = 1;

let animationRafId = null;
let animationLastFrameTime = 0;

// ------------------------------------------------------------------
// FOLLOW MODE — transitions
// ------------------------------------------------------------------
// The stage scale the camera WANTS, kept apart from the scale currently rendered. During
// a transition the two differ, and reading the rendered one back as the intent — which is
// what a sticky manual zoom would otherwise do — feeds the animation into its own input.
let followScale = 1;

// Engaging the mode, or moving it to another ship: a blend from the pose the view had to
// the pose the camera wants. Fixed durations with a defined end, unlike the open-ended
// exponential used for following a turn, because a transition has somewhere to arrive.
const FOLLOW_ENGAGE_MS = 500;
const FOLLOW_SWITCH_MS = 400;
const FOLLOW_LEAVE_MS = 400;

// {start, duration, t, fromScale, fromAngle, fromPos}
let followTransition = null;

// Leaving is its own shape: nothing is being tracked any more, so the rotation simply
// unwinds about the point the ship was drawn at, which holds the ship still on screen
// while the chart swings back to north under it. {start, duration, fromAngle}
let leaveTransition = null;

// Ease in and out, so a transition neither snaps away nor arrives abruptly. Reaches
// exactly 1 at the end, which an exponential approach never does.
function easeInOut(x) {
    return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

// How far a fixed-duration transition has got, from 0 to 1 inclusive.
function transitionProgress(transition, now) {
    if (transition.duration <= 0) return 1;
    return Math.min(1, (now - transition.start) / transition.duration);
}

// Is the CHART currently turned away from north? Not the same question as "is a ship
// being followed": for the few hundred milliseconds of the leaving animation the mode is
// already off while the chart is still visibly rotated. Everything that cares about the
// picture rather than the mode — the compass, and the north-up position clamp whose
// arithmetic assumes an axis-aligned chart — has to ask this one instead.
function isChartRotated() {
    return followedShipId !== null || leaveTransition !== null;
}

// Clamps a value into [-limit, +limit].
function clampToLimit(value, limit) {
    return Math.max(-limit, Math.min(limit, value));
}

// Starts a blend from the pose on screen right now to whatever the camera decides it
// wants. The from-pose is READ, never inferred from the mode — the layers know their own
// rotation in every state, including halfway through a leave.
function beginFollowTransition(durationMs) {
    followTransition = {
        start: performance.now(),
        duration: durationMs,
        t: 0,
        fromScale: konvaStage.scaleX(),
        fromAngle: konvaMapLayer.rotation(),
        fromPos: {x: konvaStage.x(), y: konvaStage.y()}
    };
    followAngle = followTransition.fromAngle;
}

// Ship length on screen as a fraction of viewport height, at the zoom the mode picks
// when it engages.
const FOLLOW_SHIP_HEIGHT_FRACTION = 0.25;

// How far past the most demanding ship's optimum the user may still zoom in.
const FOLLOW_SCALE_HEADROOM = 1.5;

// Hull length in map units: the silhouette spans -0.5*yy .. +0.5*yy before cfg.scale
// is applied (computeShipVerticesForKonva), so its length is exactly yy * scale.
// `pp` only shifts the shape along its own axis — it does not stretch it.
function shipLengthInMapUnits(id) {
    const cfg = modelsConfig[id];
    return cfg ? cfg.shipParams[0] * cfg.scale : 0;
}

// Stage scale at which this ship fills FOLLOW_SHIP_HEIGHT_FRACTION of the viewport
// height. Depends on the viewport, so it is recomputed rather than cached: the same
// ship wants ~4.3 on a phone and ~12.6 on a tall desktop screen.
function optimalFollowScale(id) {
    const length = shipLengthInMapUnits(id);
    if (!konvaStage || !length) return null;
    return FOLLOW_SHIP_HEIGHT_FRACTION * konvaStage.height() / length;
}

// Raises maxScale so every ship can actually reach its optimum. Called from
// fitStageToContainer(), which is the one place that knows the viewport has changed.
function updateMaxScale() {
    let required = 0;
    for (const idString of Object.keys(modelsConfig)) {
        const scale = optimalFollowScale(Number(idString));
        if (scale) required = Math.max(required, scale);
    }
    maxScale = Math.max(3, required * FOLLOW_SCALE_HEADROOM);
}

// The three layers that make up the chart. The tooltip layer is deliberately absent:
// it has to stay upright while these rotate.
// One place that turns a ship id into something to show a person.
function shipName(id) {
    const ship = ModelsOfShips.getValueFromId(id);
    return ship ? ship.name : 'Model ' + id;
}

function followContentLayers() {
    return [konvaMapLayer, konvaTrackLayer, konvaShipLayer];
}

// How far the ship has been dragged away from the middle of the viewport, in screen
// pixels. Dragging moves this rather than pausing the mode: the ship stays followed,
// simply not in the centre, which is how you give yourself more chart ahead of the bow.
let followAnchorOffset = {x: 0, y: 0};

// Screen point the followed ship is pinned to.
function followAnchor() {
    return {
        x: konvaStage.width() / 2 + followAnchorOffset.x,
        y: konvaStage.height() / 2 + followAnchorOffset.y
    };
}

// How far the anchor may travel: the ship must stay at least one of its own lengths from
// every edge. Expressed in ship lengths rather than a percentage on purpose — the margin
// then scales itself with the zoom and needs no second setting. At the optimum, where the
// hull is a quarter of the viewport height, that leaves the middle half of the height.
// The scale is a PARAMETER, not read from somewhere inside. Both callers measure the hull
// the user can actually see, and mid-transition that is the blended scale being rendered —
// not the one the camera is aiming for. An earlier attempt at this fixed one caller by
// switching it to followScale, which only moved the disagreement to the other.
function followAnchorLimit(scale) {
    const lengthOnScreen = shipLengthInMapUnits(followedShipId) * scale;
    return {
        x: Math.max(0, konvaStage.width() / 2 - lengthOnScreen),
        y: Math.max(0, konvaStage.height() / 2 - lengthOnScreen)
    };
}

// Zooming in grows the hull, and with it the margin, so an offset that was legal a moment
// ago may not be any more. Called from applyFollowCamera(), which every path goes through.
function clampFollowAnchorOffset(scale) {
    if (followedShipId === null) return;
    const limit = followAnchorLimit(scale);
    followAnchorOffset = {
        x: clampToLimit(followAnchorOffset.x, limit.x),
        y: clampToLimit(followAnchorOffset.y, limit.y)
    };
}

// Turns a drag of the stage into a move of the anchor, and enforces the limit during the
// gesture so the ship visibly stops at it instead of springing back afterwards.
//
// Deliberately a dragBoundFunc rather than a dragmove handler: the bound function is
// asked where the stage is ALLOWED to go before anything is drawn, which is the only
// place a limit can be applied without a frame of overshoot.
function followDragBound(pos) {
    const obj = KonvaObjects[followedShipId];
    if (!obj) return pos;
    const scale = konvaStage.scaleX();
    // Where the stage would sit with the ship dead centre; the drag is read as a
    // departure from that.
    const base = {
        x: konvaStage.width() / 2 - obj.drawnPos.x * scale,
        y: konvaStage.height() / 2 - obj.drawnPos.y * scale
    };
    // The drag arrives in rendered screen space, so the limit is measured there too.
    const limit = followAnchorLimit(scale);
    followAnchorOffset = {
        x: clampToLimit(pos.x - base.x, limit.x),
        y: clampToLimit(pos.y - base.y, limit.y)
    };
    return {x: base.x + followAnchorOffset.x, y: base.y + followAnchorOffset.y};
}

// Everything a camera does when it takes fresh aim at a ship: forget the manual zoom, put
// the ship back in the middle, take that ship's own optimum, and blend there from the pose
// on screen. Shared by engaging, by moving to another ship, and by ⟲.
function rebaselineFollowCamera(id, durationMs) {
    followManualZoom = false;
    followAnchorOffset = {x: 0, y: 0};
    followScale = clampScale(optimalFollowScale(id) ?? konvaStage.scaleX());
    beginFollowTransition(durationMs);
}

// Pushes the mode's state out to every control that reflects it, so a fifth indicator
// costs one edit instead of three.
function syncFollowUi() {
    syncFollowToggles();
    updateOrientationButton();
    updateFollowChip();
    updateCompass();
}

// ⟲ inside the mode: optimal zoom AND the ship back in the middle, in one gesture. It
// also drops the manual-zoom flag, or the optimum it just restored would be overwritten
// by the next resize.
function resetFollowCamera() {
    if (followedShipId === null) return;
    // Animated like every other camera move. Snapping here was an oversight, not a choice:
    // ⟲ can travel as far as a ship change does, and the stage list names it among the
    // transitions. Double click stays the sole exception, because it throws the view away.
    rebaselineFollowCamera(followedShipId, FOLLOW_SWITCH_MS);
    applyFollowCamera();
    scheduleAnimationTick();
}

// Rotates the three content layers about the followed ship and moves the stage so
// that ship lands on the anchor.
//
// Rotating the LAYERS rather than the stage is the whole trick: scale and position
// stay on the stage exactly as before, so every existing reader of
// konvaStage.scaleX() — the LOD crossfade, hit areas, arrow sizing, the tooltip's
// counter-scale — keeps working untouched. Setting offset == position == the ship's
// map point makes that point both the pivot and a fixed point of the layer
// transform, so the stage still maps it to the anchor with the same arithmetic
// north-up uses.
function applyFollowCamera() {
    if (followedShipId === null || !konvaStage) return;
    const obj = KonvaObjects[followedShipId];
    if (!obj || obj.lastUpdateTime === null) return;

    const p = obj.drawnPos;
    const wanted = clampScale(followScale);

    // The scale about to be RENDERED, worked out before the anchor is clamped so the limit
    // is measured against the hull that will be on screen.
    const t = followTransition ? followTransition.t : 1;
    const scale = t >= 1 ? wanted : followTransition.fromScale + (wanted - followTransition.fromScale) * t;

    clampFollowAnchorOffset(scale);
    const anchor = followAnchor();
    // Where the camera belongs right now. During a transition the applied pose is a blend
    // towards it, recomputed every frame rather than fixed at the start — the ship keeps
    // moving while the camera is on its way to it.
    const target = {x: anchor.x - p.x * wanted, y: anchor.y - p.y * wanted};

    const pos = t >= 1 ? target : {
        x: followTransition.fromPos.x + (target.x - followTransition.fromPos.x) * t,
        y: followTransition.fromPos.y + (target.y - followTransition.fromPos.y) * t
    };

    for (const layer of followContentLayers()) {
        layer.offset({x: p.x, y: p.y});
        layer.position({x: p.x, y: p.y});
        layer.rotation(followAngle);
    }

    konvaStage.scale({x: scale, y: scale});
    konvaStage.position(pos);

    updateShipHitAreas();
    updateShipRenderMode();
    // The needle tracks the rotation actually applied, so it belongs here rather than in
    // any of the places that decide what the rotation should be.
    updateCompass();
    // An open tooltip is anchored to a hull that this call has just moved on screen. Cheap
    // in practice: currentTooltipShipId is null unless somebody has one open.
    if (currentTooltipShipId !== null) {
        updateTooltipContent(currentTooltipShipId);
    }
    konvaStage.batchDraw();
}

// ------------------------------------------------------------------
// FOLLOW MODE — orientation source (COG or HDG)
// ------------------------------------------------------------------
// Which direction the mode puts at the top of the screen:
//   HDG — where the bow points, straight from the reported heading.
//   COG — where the ship actually travels over ground, derived from the movement
//         between the last two reported positions.
// The gap between them is the drift angle, so in COG mode the hull visibly crabs
// against the vertical, which is the point of having the choice.
//
// Labelled HDG/COG rather than "head up"/"course up" deliberately: plotter vendors
// disagree about whether "course up" means heading or course over ground, and IMO
// renamed the ship's own axis from Course to Heading precisely to end that confusion.
const ORIENTATION_HDG = 'HDG';
const ORIENTATION_COG = 'COG';

// What the user picked, and what the camera is actually able to use right now. They
// differ while a course over ground cannot be had — and WHY it cannot differs too, which
// is the whole reason the reason is recorded rather than assumed to be the speed gate.
let followOrientationMode = ORIENTATION_COG;
let followEffectiveOrientation = ORIENTATION_COG;

// Why the camera is on HDG while COG is selected: 'speed' under the gate, 'no-course'
// when there is nothing to derive a course from yet. Null when COG is actually in use.
let followFallbackReason = null;

// Below this speed the direction of travel does not exist in any useful sense: at 1 kn
// a ship covers half a metre between messages, so a few tens of centimetres of position
// noise swing the derived course by tens of degrees. Under the gate the camera falls
// back to HDG and the button says so.
const COG_MIN_SPEED_KN = typeof COG_MIN_SPEED_KN_VAR === 'number' ? COG_MIN_SPEED_KN_VAR : 1;

// Camera rotation that puts this ship's bow at the top of the screen. The hull is
// drawn at (heading + ANGLE_CORRECTION) in map coordinates, so undoing exactly that
// is what stands it upright — whatever the 9 degrees originally meant.
function bowUpAngle(headingDeg) {
    return -(headingDeg + ANGLE_CORRECTION);
}

// Direction of travel between the last two REPORTED positions, in map-frame degrees
// clockwise from map-up.
//
// No ANGLE_CORRECTION here, and that is not an oversight: this angle is measured in the
// chart's own coordinates, where the correction has no meaning. It is the reported
// heading that arrives in compass degrees and needs converting; a course derived from
// chart coordinates is already in them.
//
// Reported positions, not drawn ones — the eased render position lags, and deriving a
// course from it would feed the camera's own smoothing back into its input.
function travelDirectionMapAngle(id) {
    const obj = KonvaObjects[id];
    if (!obj || !obj.prevPos) return null;
    const dx = obj.reportedPos.x - obj.prevPos.x;
    const dy = obj.reportedPos.y - obj.prevPos.y;
    if (dx === 0 && dy === 0) return null;
    return Math.atan2(dx, -dy) * 180 / Math.PI;
}

// The rotation the camera should aim for, and — as a side effect — which orientation it
// managed to use. Deliberately no averaging window: the dead band and the 300 ms easing
// sit downstream of this and already filter the jitter, so the value fed in is the
// freshest one available rather than a smoothed history.
function followOrientationTarget(id) {
    const obj = KonvaObjects[id];
    if (!obj) return followAngle;
    if (followOrientationMode === ORIENTATION_COG) {
        if (Math.abs(obj.lastSpeed) < COG_MIN_SPEED_KN) {
            followFallbackReason = 'speed';
        } else {
            const travel = travelDirectionMapAngle(id);
            if (travel !== null) {
                followEffectiveOrientation = ORIENTATION_COG;
                followFallbackReason = null;
                return -travel;
            }
            // Fast enough, but no course to be had: a ship that has reported only once, or
            // twice from the same spot. Saying "below N kn" here would be a plain untruth.
            followFallbackReason = 'no-course';
        }
    } else {
        followFallbackReason = null;
    }
    followEffectiveOrientation = ORIENTATION_HDG;
    return bowUpAngle(obj.lastAngle);
}

// Signed difference taking the short way round, so 359 to 1 is +2 rather than -358.
function shortestAngleDelta(from, to) {
    let delta = (to - from) % 360;
    if (delta > 180) delta -= 360;
    if (delta < -180) delta += 360;
    return delta;
}

// Fraction of a remaining gap to close in this frame. Derived from dt rather than
// assumed per frame, so the motion looks the same on a 60 Hz and a 144 Hz screen and
// does not stall when a frame is dropped; easeMs is the time to cover ~95%.
function easeFraction(dtMs, easeMs) {
    return 1 - Math.pow(0.05, dtMs / easeMs);
}

// Slides every reported hull towards its latest position. Returns whether anything is
// still on its way, which is what keeps the frame loop alive.
function advanceShipPositions(dt) {
    if (SHIP_POSITION_EASE_MS <= 0) return false;
    const k = easeFraction(dt, SHIP_POSITION_EASE_MS);
    let moving = false;
    for (const idString of Object.keys(KonvaObjects)) {
        const id = Number(idString);
        const obj = KonvaObjects[id];
        if (obj.lastUpdateTime === null) continue;
        const dx = obj.reportedPos.x - obj.drawnPos.x;
        const dy = obj.reportedPos.y - obj.drawnPos.y;
        if (Math.abs(dx) + Math.abs(dy) <= SHIP_POSITION_SETTLED) {
            if (dx !== 0 || dy !== 0) {
                obj.drawnPos = {x: obj.reportedPos.x, y: obj.reportedPos.y};
                renderShipAt(id);
                updateKonvaTrack(id);
            }
            continue;
        }
        obj.drawnPos = {x: obj.drawnPos.x + dx * k, y: obj.drawnPos.y + dy * k};
        renderShipAt(id);
        updateKonvaTrack(id);
        moving = true;
    }
    return moving;
}

// One frame of an engage or ship-change blend. While it runs it OWNS the camera angle,
// so the open-ended easing does not fight it; once it ends the easing takes back over
// from wherever the blend left off, with no discontinuity.
function advanceFollowTransition(now) {
    const tr = followTransition;
    const raw = transitionProgress(tr, now);
    tr.t = easeInOut(raw);
    followAngle = tr.fromAngle + shortestAngleDelta(tr.fromAngle, followTargetAngle) * tr.t;
    if (raw >= 1) {
        followTransition = null;
        followAngle = followTargetAngle;
        return false;
    }
    return true;
}

// One frame of leaving. The mode is already off — the toggles, chip and compass went with
// it — and this only unwinds the rotation the chart still carries. The layers keep their
// pivot on the ship's drawn position, so the ship does not move on screen while the lake
// turns back to north beneath it.
function advanceLeaveTransition(now) {
    const tr = leaveTransition;
    const raw = transitionProgress(tr, now);
    const angle = tr.fromAngle * (1 - easeInOut(raw));
    for (const layer of followContentLayers()) {
        layer.rotation(angle);
    }
    if (raw >= 1) {
        leaveTransition = null;
        finishLeaving();
        return false;
    }
    konvaStage.batchDraw();
    return true;
}

// Tidies up once the rotation has fully unwound: identity transforms on the layers and a
// single pull back inside the chart, which is the one moment leaving may shift the frame —
// only if the anchor had been dragged past the edge.
function finishLeaving() {
    for (const layer of followContentLayers()) {
        layer.rotation(0);
        layer.offset({x: 0, y: 0});
        layer.position({x: 0, y: 0});
    }
    konvaStage.position(clampStagePosition(konvaStage.position(), konvaStage.scaleX()));
    updateShipHitAreas();
    updateShipRenderMode();
    // The compass hides on the chart being straight, not on the mode ending, so this is the
    // moment it goes — and the only one: applyFollowCamera(), which refreshes it the rest of
    // the time, does not run once the mode is off.
    updateCompass();
    konvaStage.batchDraw();
}

// Turns the chart towards the target heading. Only meaningful while following.
function advanceFollowAngle(dt) {
    if (followedShipId === null) return false;
    const delta = shortestAngleDelta(followAngle, followTargetAngle);
    if (Math.abs(delta) <= FOLLOW_ANGLE_SETTLED) {
        if (followAngle === followTargetAngle) return false;
        followAngle = followTargetAngle;
        return false;
    }
    followAngle += delta * easeFraction(dt, FOLLOW_EASE_MS);
    return true;
}

// One frame of everything that moves gradually. The loop exists only while something is
// actually in motion and stops rescheduling the moment both the hulls and the chart
// angle have arrived — a fleet lying still costs nothing. That matters more here than
// usual: while following, each frame redraws the map layer, which at 6-12x zoom is a
// heavily magnified raster.
function animationTick(now) {
    animationRafId = null;
    const dt = animationLastFrameTime ? Math.min(50, now - animationLastFrameTime) : 16;
    animationLastFrameTime = now;

    const shipsMoving = advanceShipPositions(dt);
    // A running transition owns the camera angle; the open-ended easing only takes over
    // once it has arrived.
    const angleMoving = followTransition ? advanceFollowTransition(now) : advanceFollowAngle(dt);
    const leaving = leaveTransition ? advanceLeaveTransition(now) : false;

    if (followedShipId !== null) {
        // Pins the followed ship to the anchor at its DRAWN position, so the hull holds
        // still while the chart glides underneath — the whole point of easing both.
        applyFollowCamera();
    } else {
        konvaShipLayer.batchDraw();
    }

    if (shipsMoving || angleMoving || leaving) {
        animationRafId = requestAnimationFrame(animationTick);
    } else {
        animationLastFrameTime = 0;
    }
}

function scheduleAnimationTick() {
    if (animationRafId !== null) return;
    animationLastFrameTime = 0;
    animationRafId = requestAnimationFrame(animationTick);
}

// Aims the camera at the followed ship's current orientation, unless the change is small
// enough to be noise. The dead band is applied to the resulting camera angle, so it
// filters a jumpy derived course exactly as it filters a jumpy reported heading.
function setFollowTargetAngle(id) {
    const wanted = followOrientationTarget(id);
    updateOrientationButton();
    if (Math.abs(shortestAngleDelta(followTargetAngle, wanted)) < FOLLOW_ANGLE_DEAD_BAND) return;
    followTargetAngle = wanted;
    scheduleAnimationTick();
}

// Zoom while following. The point-preserving arithmetic of zoomStageAtPoint() has
// nothing to do here: it inverts the stage transform alone, which no longer describes
// the picture once the layers carry a rotation. It is also unnecessary — the ship is
// pinned to the anchor, so applyFollowCamera() recomputes the stage position from the
// ship's map point and the zoom is anchored on the ship by construction, whichever way
// the request arrived (wheel, pinch, or the +/- buttons).
function followZoomTo(newScaleRaw) {
    followManualZoom = true;
    followScale = clampScale(newScaleRaw);
    // A zoom mid-transition is the user overtaking the animation; let them have it.
    followTransition = null;
    // applyFollowCamera() refreshes an open tooltip itself; repeating it here was left
    // behind when that moved into the camera.
    applyFollowCamera();
}

function followShip(id) {
    const obj = KonvaObjects[id];
    if (!obj || !konvaStage) return;
    // Captured BEFORE anything changes: engaging from the whole chart is a move of the
    // camera across the lake and several times into it, and cutting straight there is
    // disorienting. A blend from this pose is what makes it readable.
    // Coming from the whole chart is a longer journey than moving between two ships; a
    // half-finished leave counts as the latter, since the view is still zoomed in.
    const fromOverview = followedShipId === null && leaveTransition === null;

    // A part-played leave would keep unwinding a rotation the new camera is setting.
    leaveTransition = null;

    followedShipId = id;
    followTargetAngle = followOrientationTarget(id);
    // The optimum belongs to this ship, so a zoom the user set for the previous one is
    // not carried over — Lady Marie at Kołobrzeg's optimum would fill 37% of the height.
    // The anchor goes back to the middle for the same reason: an offset chosen for one
    // ship's silhouette means something else against another's.
    rebaselineFollowCamera(id, fromOverview ? FOLLOW_ENGAGE_MS : FOLLOW_SWITCH_MS);

    applyFollowCamera();
    scheduleAnimationTick();
    syncFollowUi();
    // On a phone the ship list is a sheet covering the map, so the moment it has been used
    // to pick a ship it is in the way of the thing it just selected. It closes, and the
    // chip — a button at every width for exactly this reason — becomes the way back out.
    // No-op on desktop, where the list is a sidebar beside the map rather than over it.
    setShipListOpen(false);
}

// Leaving keeps your zoom and your frame and only takes the rotation away — the chart
// swings back to north under a ship that does not move on screen. Zeroing offset and
// position afterwards is a no-op while rotation is 0 (the layer transform collapses to
// translate(position - offset), and the two are equal), it just leaves the layers clean.
// The one visible exception is the clamp at the end: a view dragged past the edge of the
// chart is pulled back in once, because the north-up rule takes over again.
// Note this does NOT stop the frame loop: hulls keep easing towards their reported
// positions in the ordinary north-up view too, so the loop belongs to the ships, not
// to the mode.
function unfollowShip() {
    if (followedShipId === null) return;
    // The mode goes off at once — floors, clamps and the whole UI switch to north-up
    // immediately — while the rotation the chart still carries unwinds over the next
    // few hundred milliseconds. Nothing is being tracked during that, so the layers
    // simply keep the pivot they already have.
    const fromAngle = followAngle;
    followTransition = null;
    followedShipId = null;
    leaveTransition = {start: performance.now(), duration: FOLLOW_LEAVE_MS, fromAngle};
    scheduleAnimationTick();

    syncFollowUi();
}

// Leaving with no animation at all: used by the gesture that also refits the chart, where
// unwinding a rotation only to throw the view away a moment later would be motion for
// nothing.
function unfollowShipImmediately() {
    followTransition = null;
    leaveTransition = null;
    if (followedShipId !== null) {
        followedShipId = null;
        syncFollowUi();
    }
    finishLeaving();
}

// ------------------------------------------------------------------
// FOLLOW MODE — ship list toggles
// ------------------------------------------------------------------
// The list only ever REFLECTS followedShipId; it is never asked what the mode is. That
// direction matters: reading state back out of markup is what silently broke the map
// tooltip once the list was restyled.
function followToggleFor(id) {
    return getCachedElement('follow' + id);
}

function syncFollowToggles() {
    for (const idString of Object.keys(modelsConfig)) {
        const id = Number(idString);
        const button = followToggleFor(id);
        if (!button) continue;
        const on = id === followedShipId;
        button.setAttribute('aria-pressed', on ? 'true' : 'false');
        const row = button.closest('.ship-row');
        if (row) row.classList.toggle('following', on);
        updateFollowToggleTooltip(id);
    }
}

// A ship with no message behind it has no position to aim a camera at, so its toggle
// stays unavailable until the first one lands — and then turns available by itself.
function updateFollowToggleAvailability(id) {
    const button = followToggleFor(id);
    if (!button) return;
    const obj = KonvaObjects[id];
    button.setAttribute('aria-disabled', obj && obj.lastUpdateTime !== null ? 'false' : 'true');
    updateFollowToggleTooltip(id);
}

// Bootstrap tooltip instances, one per toggle, kept so their text can be rewritten in place.
const followTooltips = {};

// What the hover tooltip should say right now. Three states, not two: a ship that has never
// reported has an unavailable toggle, and that is the one moment worth explaining WHY rather
// than repeating the label underneath the cursor.
function followTooltipText(id) {
    const button = followToggleFor(id);
    const name = shipName(id);
    if (button && button.getAttribute('aria-disabled') === 'true') {
        return name + ' has not reported yet';
    }
    return id === followedShipId ? 'Stop following ' + name : 'Follow ' + name;
}

// Bootstrap caches a tooltip's content at construction, so changing the attribute alone leaves
// a followed ship still offering to be followed. setContent() is what actually moves it.
function updateFollowToggleTooltip(id) {
    const button = followToggleFor(id);
    if (!button) return;
    const text = followTooltipText(id);
    button.setAttribute('data-bs-title', text);
    const tooltip = followTooltips[id];
    if (tooltip) tooltip.setContent({'.tooltip-inner': text});
}

// ------------------------------------------------------------------
// FOLLOW MODE — compass and chip
// ------------------------------------------------------------------
// Screen direction of chart north, in degrees clockwise from up.
//
// The layers are rotated by followAngle, so raster-up points that way on screen; north
// sits ANGLE_CORRECTION clockwise of raster-up (that is what the 9 degrees mean, seen
// from this side). Hence the sum — and it holds whichever orientation source the camera
// is using, HDG or COG, because it is derived from the rotation actually applied rather
// than from any heading.
function compassAngle() {
    return followAngle + ANGLE_CORRECTION;
}

// The needle turns; the letter rides round with it but stays upright. Written as SVG
// transform lists rather than CSS, so there is no transform-origin to get wrong: the
// label's list undoes the rotation innermost, so the glyph ends up unrotated while its
// position has travelled.
function updateCompass() {
    const button = getCachedElement('compassBtn');
    if (!button) return;
    // Tied to the chart being rotated, not to the mode: keyed on the mode it vanished at
    // the START of the leaving animation, while the lake was still visibly turning.
    if (!isChartRotated()) {
        button.hidden = true;
        return;
    }
    button.hidden = false;

    const angle = compassAngle();
    const needle = getCachedElement('compassNeedle');
    const label = getCachedElement('compassLabel');
    if (needle) needle.setAttribute('transform', 'rotate(' + angle + ' 20 20)');
    if (label) {
        label.setAttribute('transform',
            'translate(20 20) rotate(' + angle + ') translate(0 -15) rotate(' + (-angle) + ')');
    }
}

// ------------------------------------------------------------------
// FOLLOW MODE — silence watchdog
// ------------------------------------------------------------------
// Seconds of quiet after which the followed ship counts as having stopped reporting. The
// camera does not move: it holds the last known position and the chip says how old it is.
const FOLLOW_SILENCE_WARN_S = 60;

// Below this the age is not worth showing at all. Messages arrive about once a second, so
// a live number would flicker between 0 and 1 for ever and pull the eye to the one moment
// when nothing is wrong.
const FOLLOW_STATUS_GRACE_S = 3;

let followStatusTimer = null;
let followSilenceAnnounced = false;

function followSilenceSeconds() {
    const obj = KonvaObjects[followedShipId];
    if (!obj || obj.lastUpdateTime === null) return 0;
    return (Date.now() - obj.lastUpdateTime) / 1000;
}

// Announced only when the state flips, never while the number ticks — the whole reason
// neither half of the chip is a live region. A screen reader hears "no data" once, and
// "receiving" once when it comes back.
function announceSilence(warning) {
    if (warning === followSilenceAnnounced) return;
    followSilenceAnnounced = warning;
    const alertEl = getCachedElement('followAlert');
    if (!alertEl) return;
    const name = followedShipId === null ? 'the followed ship' : shipName(followedShipId);
    alertEl.textContent = warning
        ? 'No data from ' + name + ' for over ' + FOLLOW_SILENCE_WARN_S + ' seconds'
        : (followedShipId === null ? '' : 'Receiving data from ' + name + ' again');
}

function updateFollowStatus() {
    const status = getCachedElement('followChipStatus');
    if (!status || followedShipId === null) return;
    const seconds = followSilenceSeconds();
    const warning = seconds >= FOLLOW_SILENCE_WARN_S;
    status.textContent = seconds < FOLLOW_STATUS_GRACE_S
        ? 'live'
        : (warning ? 'no data ' + Math.floor(seconds) + ' s' : Math.floor(seconds) + ' s');
    status.classList.toggle('stale', warning);
    announceSilence(warning);
}

// A timer of its own rather than a ride on the frame loop: that loop sleeps when nothing
// moves, and a ship falling silent is precisely when nothing moves.
function startFollowStatusTimer() {
    stopFollowStatusTimer();
    followSilenceAnnounced = false;
    updateFollowStatus();
    followStatusTimer = setInterval(updateFollowStatus, 1000);
}

function stopFollowStatusTimer() {
    if (followStatusTimer !== null) {
        clearInterval(followStatusTimer);
        followStatusTimer = null;
    }
    const alertEl = getCachedElement('followAlert');
    if (alertEl) alertEl.textContent = '';
}

// Shows or hides one of the mode's controls and answers whether it is now on screen, so
// the three of them stop repeating the same four lines.
function showWhileFollowing(elementId) {
    const element = getCachedElement(elementId);
    if (!element) return null;
    element.hidden = followedShipId === null;
    return element.hidden ? null : element;
}

function updateFollowChip() {
    const chip = showWhileFollowing('followChip');
    if (!chip) {
        stopFollowStatusTimer();
        return;
    }
    startFollowStatusTimer();

    const button = getCachedElement('followChipBtn');
    if (!button) return;
    const name = shipName(followedShipId);
    // The verb is VISIBLE, not merely announced. Showing the bare ship name left sighted
    // users with a button that did not say what pressing it would do, while screen reader
    // users were told — the accessible name and the visible one have to agree.
    // Fixed for as long as the mode is on: the age of the last message lives in the status
    // element beside it, never in here, or the button's name would change every second.
    button.textContent = 'Stop following ' + name;
}

function bindFollowChip() {
    const button = getCachedElement('followChipBtn');
    if (button) button.addEventListener('click', () => unfollowShip());
    updateFollowChip();
}

function bindCompass() {
    const button = getCachedElement('compassBtn');
    // Tapping a compass means "reset bearing to north" everywhere else; north here means
    // not following, so that is what it does.
    if (button) button.addEventListener('click', () => unfollowShip());
    updateCompass();
}

// The orientation button carries the mode as its own text, because there is no honest
// picture of the difference between "where the bow points" and "where the ship goes".
// It shows the EFFECTIVE orientation, not merely the chosen one: while the ship is under
// the speed gate the camera is using HDG, and a button still reading COG would be
// claiming something the map is not doing. The auto class marks that case, so a fallback
// never looks like a setting the user made.
function updateOrientationButton() {
    const button = showWhileFollowing('orientationBtn');
    if (!button) return;

    const fallingBack = followOrientationMode === ORIENTATION_COG
        && followEffectiveOrientation === ORIENTATION_HDG;
    button.textContent = followEffectiveOrientation;
    button.classList.toggle('auto', fallingBack);
    button.setAttribute('aria-label', orientationButtonLabel(fallingBack));
}

// The spoken label states the ACTUAL reason for a fallback. Blaming the speed gate for
// every one of them was wrong for a ship that has simply not moved twice yet.
function orientationButtonLabel(fallingBack) {
    if (!fallingBack) {
        return 'Chart orientation: ' + (followEffectiveOrientation === ORIENTATION_COG
            ? 'course over ground' : 'heading');
    }
    if (followFallbackReason === 'no-course') {
        return 'Chart orientation: course over ground selected, using heading until the ship has moved';
    }
    return 'Chart orientation: course over ground selected, using heading below '
        + COG_MIN_SPEED_KN + ' kn';
}

function bindOrientationButton() {
    const button = getCachedElement('orientationBtn');
    if (!button) return;
    button.addEventListener('click', () => {
        followOrientationMode = followOrientationMode === ORIENTATION_COG
            ? ORIENTATION_HDG : ORIENTATION_COG;
        if (followedShipId !== null) {
            // Re-aim at once rather than waiting for the next message: a second of a
            // stale orientation after a deliberate tap reads as a control that missed.
            followTargetAngle = followOrientationTarget(followedShipId);
            scheduleAnimationTick();
        }
        updateOrientationButton();
    });
    updateOrientationButton();
}

function bindFollowToggles() {
    for (const idString of Object.keys(modelsConfig)) {
        const id = Number(idString);
        const button = followToggleFor(id);
        if (!button) continue;
        button.addEventListener('click', () => {
            // aria-disabled does not block clicks the way disabled does — that is exactly
            // the trade made for keeping the control in the tab order before its first
            // message — so the guard has to be here.
            if (button.getAttribute('aria-disabled') === 'true') return;
            if (followedShipId === id) {
                unfollowShip();
            } else {
                followShip(id);
            }
        });
        // Options come from the data attributes in the markup, where the reason for
        // container="body" is written down. Guarded because the verification harness serves
        // this file without Bootstrap: no bundle, no tooltips, everything else unaffected.
        if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
            followTooltips[id] = new bootstrap.Tooltip(button);
        }
        updateFollowToggleAvailability(id);
    }
    syncFollowToggles();
}

function updateShipHitAreas() {
    if (!konvaStage) return;
    const scale = konvaStage.scaleX();
    for (const id of Object.keys(KonvaObjects)) {
        KonvaObjects[id].shipShape.hitStrokeWidth(SHIP_HIT_SCREEN_PX / scale);
    }
}

// LOD crossfade: hull silhouette at high zoom, direction arrow at low zoom, so
// ships stay legible however far out the map is zoomed. Called on every zoom
// change (fitStageToContainer, zoomStageAtPoint) — also the only place that
// recomputes the arrow's points on a zoom-only change (no position update), so
// it keeps its constant on-screen size (SHIP_ARROW_SCREEN_PX) instead of
// shrinking/growing with the map like the hull does.
function updateShipRenderMode() {
    if (!konvaStage) return;
    const scale = konvaStage.scaleX();
    const t = Math.max(0, Math.min(1, (scale - LOD_ARROW_SCALE) / (LOD_HULL_SCALE - LOD_ARROW_SCALE)));
    const arrowSize = SHIP_ARROW_SCREEN_PX / scale;
    for (const id of Object.keys(KonvaObjects)) {
        const obj = KonvaObjects[id];
        if (obj.lastUpdateTime === null) {
            // no message received yet for this ship — drawnPos/lastAngle are still
            // the {0,0}/0 defaults, so drawing either shape would show a "ghost
            // ship" at the map origin. Keep both hidden until the first message.
            obj.shipShape.opacity(0);
            obj.arrowShape.opacity(0);
            // Opacity 0 does not remove a shape from Konva's hit graph, so without
            // this a tap on the map origin would open a tooltip for a ship that has
            // never reported, showing 0.0 kn / 0.0 deg / No update.
            obj.shipShape.listening(false);
            continue;
        }
        // Re-arm hit testing: this ship has reported, so it is on the map now.
        // Only the hull — the arrow is deliberately created with listening:false and
        // must stay that way. It is added to the layer after the hull, so it sits on
        // top of the hit graph, and opacity does not remove a shape from that graph.
        // Making it listen would swallow taps (it has no click handler, so the event
        // reaches the stage handler that HIDES the tooltip) — at low zoom, where the
        // arrow covers the whole ship, tapping a ship would never open its tooltip.
        obj.shipShape.listening(true);
        obj.shipShape.opacity(t);
        obj.arrowShape.opacity(1 - t);
        obj.arrowShape.points(computeShipArrowVerticesForKonva(obj.drawnPos.x, obj.drawnPos.y, obj.lastAngle, arrowSize));
    }
}

// keep the map covering the viewport; center it when smaller than the viewport
function clampStagePosition(pos, scale) {
    // Follow mode places the stage itself (applyFollowCamera) and the chart is
    // rotated underneath, so the axis-aligned "the map must cover the viewport"
    // rule below no longer describes what is on screen. Clamping to the rotated
    // map's bounds is a stage-5 job, once panning can actually pause the mode.
    if (isChartRotated()) return pos;
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
    // The follow optimum is a fraction of the viewport height, so the ceiling that
    // has to accommodate it moves with the viewport too.
    updateMaxScale();

    // Following: a resize re-baselines the camera rather than fitting the chart. The
    // optimum is a fraction of the viewport height, so it moved along with the window —
    // but a zoom the user set by hand is sticky and only gets re-clamped against the new
    // ceiling. resetView is meaningless here; leaving the mode is what returns to a fit.
    if (followedShipId !== null) {
        // The offset was measured against the old viewport, so it does not survive.
        followAnchorOffset = {x: 0, y: 0};
        const optimal = optimalFollowScale(followedShipId);
        // followScale, not the rendered scale: mid-transition the two differ, and reading
        // the rendered one back would freeze the animation at wherever it had got to.
        const wanted = followManualZoom || optimal === null ? followScale : optimal;
        followScale = clampScale(wanted);
        applyFollowCamera();
        return;
    }

    const fitWidthScale = clampScale(konvaStage.width() / MAP_WIDTH);
    const scale = resetView ? fitWidthScale : clampScale(konvaStage.scaleX());
    konvaStage.scale({x: scale, y: scale});
    konvaStage.position(clampStagePosition(konvaStage.position(), scale));
    updateShipHitAreas();
    updateShipRenderMode();
    konvaStage.batchDraw();
}

// zoom keeping the given viewport point fixed (mouse cursor / pinch center)
function zoomStageAtPoint(pointer, newScaleRaw) {
    // Every zoom path funnels through here — wheel, pinch, and the +/- buttons — so this
    // one hand-off is all the mode needs to keep its zoom anchored on the ship.
    if (followedShipId !== null) {
        followZoomTo(newScaleRaw);
        return;
    }
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
    updateShipRenderMode();
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
    konvaStage.dragBoundFunc((pos) => followedShipId !== null
        ? followDragBound(pos)
        : clampStagePosition(pos, konvaStage.scaleX()));
    // Note the asymmetry: the anchor drag belongs to the MODE, the clamp to the PICTURE.
    // During the leaving animation there is no anchor to move any more, but the chart is
    // still rotated, so clampStagePosition() has to keep its hands off — which it does,
    // because it asks isChartRotated() rather than repeating the test above.

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
    // Double click means "show me everything". While following it therefore leaves first
    // and fits second — that order matters, or the fit would be held at the mode's zoom
    // floor. It is also the one gesture that does not animate: unwinding the rotation only
    // to throw the whole view away a moment later would be motion for nothing.
    konvaStage.on('dblclick dbltap', () => {
        unfollowShipImmediately();
        fitStageToContainer(true);
    });
}

// zoom buttons overlay (+ / - / reset)
function bindMapControls() {
    const zoomInBtn = document.getElementById('zoomInBtn');
    const zoomOutBtn = document.getElementById('zoomOutBtn');
    const zoomResetBtn = document.getElementById('zoomResetBtn');
    const viewportCenter = () => ({x: konvaStage.width() / 2, y: konvaStage.height() / 2});
    if (zoomInBtn) zoomInBtn.addEventListener('click', () => zoomStageAtPoint(viewportCenter(), konvaStage.scaleX() * 1.3));
    if (zoomOutBtn) zoomOutBtn.addEventListener('click', () => zoomStageAtPoint(viewportCenter(), konvaStage.scaleX() / 1.3));
    // Inside the mode ⟲ tidies the camera up rather than fitting the whole chart: fitting
    // would mean leaving, and leaving is what the toggle and the compass are for.
    if (zoomResetBtn) {
        zoomResetBtn.addEventListener('click', () => {
            if (followedShipId !== null) {
                resetFollowCamera();
            } else {
                fitStageToContainer(true);
            }
        });
    }
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

// Ship list bottom sheet (mobile) / sidebar (desktop) toggle. Deliberately not
// Bootstrap's offcanvas component: that would need its own copy of the list
// with its own ids, and getElementById() only ever finds the first of two
// duplicate ids — chart.html renders the ship list once, this just shows/hides it.
// Lifted out of bindShipListToggle() so engaging follow mode can close the sheet too. On
// desktop the panel is a static sidebar and the class means nothing there, so this is
// safe to call at any width — the media query decides whether it has an effect.
function setShipListOpen(open) {
    const panel = document.getElementById('shipListPanel');
    const trigger = document.getElementById('shipListTriggerBtn');
    const backdrop = document.getElementById('shipListBackdrop');
    if (!panel || !trigger) return;
    panel.classList.toggle('open', open);
    backdrop?.classList.toggle('open', open);
    trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
}

function bindShipListToggle() {
    const trigger = document.getElementById('shipListTriggerBtn');
    const closeBtn = document.getElementById('shipListCloseBtn');
    const backdrop = document.getElementById('shipListBackdrop');
    if (!trigger) return;
    trigger.addEventListener('click', () => setShipListOpen(true));
    closeBtn?.addEventListener('click', () => setShipListOpen(false));
    backdrop?.addEventListener('click', () => setShipListOpen(false));
}
bindShipListToggle();

// Raw diagnostics line (#textField) — hidden by default (Faza 5), shown on demand.
// Its content is still updated regardless of visibility (see ws.onmessage), this
// only toggles whether it's on screen.
function bindDiagnosticsToggle() {
    const btn = document.getElementById('diagnosticsToggleBtn');
    const wrapper = document.getElementById('textFieldWrapper');
    if (!btn || !wrapper) return;
    btn.addEventListener('click', () => wrapper.classList.toggle('d-none'));
}
bindDiagnosticsToggle();

let mapa_x = 2.407; /// było 2.4   = kalibracja mapy

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

// Shared rotate+translate+flatten step for both the hull and the LOD arrow:
// rotates by (angle + ANGLE_CORRECTION), translates to (x,y), then flattens to
// Konva's [x1,y1,x2,y2,...] point format. Kept in one place so a future change
// to that convention only has to happen once — the ship-list arrow
// (renderShipListArrow) deliberately does NOT use this helper, see the
// comment there for why.
function rotateTranslateFlatten(vertices, angle, x, y) {
    const radians = (angle + ANGLE_CORRECTION) * Math.PI / 180;
    const flat = [];
    for (const v of vertices) {
        flat.push(v.x * Math.cos(radians) - v.y * Math.sin(radians) + x);
        flat.push(v.x * Math.sin(radians) + v.y * Math.cos(radians) + y);
    }
    return flat;
}

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

    return rotateTranslateFlatten(vertices, angle, x, y);
}

// LOD arrow shown instead of the hull silhouette at low zoom — same triangle as
// the ship-list arrow (chart.html), M12 2 L18 20 L12 16 L6 20 Z, viewBox 24x24,
// vertices taken relative to the center (12,12) then normalized to `size`.
// `size` is expected in SCREEN pixels, not map units (see SHIP_ARROW_SCREEN_PX) —
// unlike the hull, this arrow must stay a constant, legible size at any zoom,
// which is the whole point of falling back to it when the map is zoomed out.
function computeShipArrowVerticesForKonva(x, y, angle, size) {
    const vertices = [
        { x: 0, y: -10 }, { x: 6, y: 8 }, { x: 0, y: 4 }, { x: -6, y: 8 }
    ].map(v => ({ x: v.x * size / 24, y: v.y * size / 24 }));
    // SAME correction as the hull (computeShipVerticesForKonva, via the shared
    // rotateTranslateFlatten helper) — this arrow replaces the hull in place
    // during the crossfade, so it must point the same way.
    return rotateTranslateFlatten(vertices, angle, x, y);
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
            // SCREEN pixels, not map units. As a map-unit width this line was 0.9 px across
            // the whole chart and 24 px at full zoom — too faint to follow at one end and a
            // ribbon at the other. Screen-space line widths are what Leaflet's `weight` and
            // MapLibre's `line-width` both mean, and strokeScaleEnabled is how Konva says it.
            // Verified against the stage transform, not just a shape's own scale: 2 px at
            // every scale from 1 to 12.
            strokeWidth: TRACK_STROKE_PX,
            strokeScaleEnabled: false,
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
            // Screen pixels for the same reason as the track: scaled, this outline reached
            // 12 px at full zoom and began to dominate the silhouette it was meant to
            // define. The hull's SIZE still scales with the chart — that is a functional
            // requirement — only the line around it holds still.
            strokeWidth: HULL_STROKE_PX,
            strokeScaleEnabled: false,
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

        // LOD arrow: replaces shipShape at low zoom (updateShipRenderMode crossfades
        // opacity between the two). Not clickable — shipShape keeps that job even
        // while faded out, so tap targets don't change as the user zooms.
        const arrowShape = new Konva.Line({
            points: computeShipArrowVerticesForKonva(0, 0, 0, SHIP_ARROW_SCREEN_PX / konvaStage.scaleX()),
            fill: ModelsOfShips.getColorFromId(id),
            closed: true,
            listening: false,
            opacity: 0
        });
        konvaShipLayer.add(arrowShape);

        KonvaObjects[id] = {
            trackLine,
            shipShape,
            arrowShape,
            // Everything that asks where a ship is on screen — the LOD arrow, the tooltip
            // anchor, the follow camera — reads drawnPos, so it keeps agreeing with what
            // the eye sees while the two converge.
            drawnPos: { x: 0, y: 0 },
            reportedPos: { x: 0, y: 0 },
            prevPos: null,
            // Counts reports rather than inferring "first one" from a timestamp the caller
            // has already written by then. See updateKonvaShip().
            reportCount: 0,
            lastAngle: 0,
            lastSpeed: 0,
            lastHeading: 0,
            lastUpdateTime: null
        };
    }
    konvaTrackLayer.draw();
    konvaShipLayer.draw();
    // fitStageToContainer() already ran (initKonvaStage, before this function) with
    // an empty KonvaObjects — its updateShipRenderMode() call was a no-op then, so
    // ships need this one to start with correct opacity for the current zoom level.
    updateShipRenderMode();
}

// Redraws one hull wherever drawnPos currently says it is. Called both when a message
// lands and on every frame of the easing, so it must stay cheap: it only recomputes the
// two point arrays, it does not draw.
// cfg.scale is constant: silhouette size reflects the real ship size relative to the map at any zoom
function renderShipAt(id) {
    const obj = KonvaObjects[id];
    const cfg = modelsConfig[id];
    if (!obj || !cfg) return;
    obj.shipShape.points(
        computeShipVerticesForKonva(obj.drawnPos.x, obj.drawnPos.y, cfg.scale, obj.lastAngle, ...cfg.shipParams));
}

// Updates one ship's Konva shape (position and rotation).
// batchDraw() coalesces drawing through requestAnimationFrame, so no counter is needed.
function updateKonvaShip(id, x, y, angle) {
    const obj = KonvaObjects[id];
    const cfg = modelsConfig[id];
    if (!obj || !cfg) return;

    // "Is this the ship's first report?" cannot be asked of lastUpdateTime here: the
    // caller stamps it BEFORE calling this function — it has to, because
    // updateShipRenderMode() reads it to decide whether the ship is on the chart at all —
    // so by this point it is never null and every message looks like a later one. Both
    // guards below need the real answer, and both fail invisibly without it: the hull
    // would ease in from the map origin on its first appearance, and the derived course
    // would be measured from that same origin, swinging the chart to a bogus bearing.
    const firstReport = obj.reportCount === 0;

    // The position this message supersedes, kept because a direction of travel can only be
    // measured between two reports. Absent until a second one arrives, which is what makes
    // COG unavailable for a ship that has only just appeared.
    if (!firstReport) {
        obj.prevPos = obj.reportedPos;
    }
    obj.reportedPos = { x, y };
    obj.lastAngle = angle;
    // With smoothing off, or on the very first report (there is nothing to slide from),
    // the drawn position is the reported one and nothing has to be animated.
    if (SHIP_POSITION_EASE_MS <= 0 || firstReport) {
        obj.drawnPos = { x, y };
    }
    obj.reportCount++;
    renderShipAt(id);

    // Recomputes this ship's arrow points (needs the fresh drawnPos/lastAngle set
    // just above) and its hull/arrow opacity for the current zoom — a position
    // update is also how a previously-offline ship (opacity forced to 0 in
    // updateShipRenderMode) becomes visible for the first time, so this can't
    // be skipped even though zoom changes already call it separately.
    updateShipRenderMode();
    konvaShipLayer.batchDraw();

    // A new position for the followed ship is also a new camera pose. The heading goes
    // through the dead band; both it and the position are then eased by the frame loop.
    if (id === followedShipId) {
        setFollowTargetAngle(id);
        applyFollowCamera();
    }
    scheduleAnimationTick();

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

    // Konva wants a flat [x1, y1, x2, y2, ...] array.
    // The last reported point is replaced by the position the hull is actually DRAWN at,
    // so the line always ends at the bow instead of running past it while the ship eases
    // into place — at 12 kn and full zoom that overshoot would be a 120 px spike sticking
    // out ahead of the ship. The substitute always lies on the segment between the last
    // two reported points, so nothing is invented, and once the ship settles the two
    // coincide exactly.
    const pts = [];
    for (let i = 0; i < cfg.track.length - 1; i++) {
        pts.push(cfg.track[i].x);
        pts.push(cfg.track[i].y);
    }
    if (cfg.track.length > 0) {
        pts.push(obj.drawnPos.x);
        pts.push(obj.drawnPos.y);
    }
    obj.trackLine.points(pts);
    konvaTrackLayer.batchDraw();
}

// ------------------------------------------------------------------
// Funkcja do aktualizacji wyświetlania modelu
// ------------------------------------------------------------------
function updateModelDisplay(config, modelId, positionX, positionY, angle, speed) {
    fillFieldValues(config.headingField, angle);
    fillFieldValues(config.speedField, speed);
    renderShipListArrow('arrow' + modelId, angle);
    ledBlink(config.led);
    fillPacketCounter(config.rsField, ShipCounter.incrementIntMap(modelId));

    if (KonvaObjects[modelId]) {
        KonvaObjects[modelId].lastSpeed = speed;
        KonvaObjects[modelId].lastHeading = angle;
        KonvaObjects[modelId].lastUpdateTime = Date.now();
        updateKonvaShip(modelId, positionX, positionY, angle);
    }

    // Strictly AFTER lastUpdateTime is stamped above: that field is what "this ship has
    // reported" means, and asking before it is written leaves the toggle unavailable for
    // one more message. The gap is invisible to any test that waits a second or two
    // before looking, which is precisely how it survived being written.
    updateFollowToggleAvailability(modelId);

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
        // '°' only appears once a real value has arrived — the initial "—" placeholder
        // (ship list, offline) stays suffix-free until this overwrites it.
        spanElement.innerHTML = value.toFixed(1).padStart(4, '0') + '°';
    } else {
        spanElement.innerHTML = value.toFixed(1);
    }
}

// Rotates the ship-list course arrow (chart.html, compact format: "↗ 214° · 8.2 kn").
// No ANGLE_CORRECTION here: unlike the map hull silhouette, this arrow has no
// companion shape it needs to visually match, so it shows the raw compass heading.
function renderShipListArrow(elementId, angleDeg) {
    const el = getCachedElement(elementId);
    if (!el) return;
    el.style.visibility = 'visible';
    el.style.transform = `rotate(${angleDeg}deg)`;
}

/**
 * Zero-padded to three characters so the value always fills the 3ch box .rx-count reserves --
 * ShipCounter wraps at 999, so three is also the maximum.
 *
 * <p>Anything that is not a number becomes "---" rather than its text. ShipCounter.incrementIntMap
 * returns undefined for an id its own map does not hold, and String(undefined) is nine characters
 * -- which would spill straight out of a box sized for three and across the row. The two id lists
 * agree today, but they are declared independently and hundreds of lines apart.</p>
 */
function fillPacketCounter(elementId, value) {
    const spanElement = getCachedElement(elementId);
    if (!spanElement) return;
    spanElement.textContent = Number.isInteger(value)
        ? String(value).padStart(3, '0')
        : '---';
}

function dropLedOn(event) {
    const element = event.currentTarget;
    if (element.getAnimations().length > 0) {
        // animationend is queued, not delivered instantly. A message that arrived in the gap
        // between the animation ending and this handler running has already restarted the blink,
        // and stripping the class now would cancel it -- that message's blink would simply never
        // appear. Leave it on and hand the tidying to the restarted animation's own event.
        element.addEventListener('animationend', dropLedOn, {once: true});
        return;
    }
    element.classList.remove('led-on');
}

/**
 * Reports that a message arrived. How the dot then behaves -- and for how long -- belongs to
 * the fb-led-blink keyframes in chart.css; nothing here knows a duration.
 *
 * <p>The ordinary paths force no layout. The obvious way to replay a CSS animation -- drop the
 * class, read offsetWidth, add it back -- buys the restart with a synchronous full-document
 * layout flush, and at six ships sending about once a second that is six flushes per second next
 * to the Konva stage redraw. Only the recovery branch below still pays it, and it is not reached
 * in normal operation.</p>
 */
function ledBlink(elementId) {
    const element = getCachedElement(elementId);
    if (!element) return;

    const running = element.getAnimations();
    if (running.length > 0) {
        // A message landed mid-blink: seek back to the start so it is shown rather than
        // swallowed by the tail of the one before -- which is what a burst of frames buffered
        // during a WebSocket outage delivers.
        running.forEach(animation => {
            animation.currentTime = 0;
        });
        return;
    }

    if (element.classList.contains('led-on')) {
        // The class is still on but nothing is animating behind it: the animation was cancelled,
        // or never began because CSS was not animating this element when it was added. Adding a
        // class that is already there is not a change, so the dot would stay dark for good --
        // the setTimeout this replaced could not get stuck like that, because its timer always
        // took the class back off. Forcing a layout read between the remove and the add is what
        // makes the browser see two states instead of none. Recovery only; the ordinary path
        // below never reaches it.
        element.classList.remove('led-on');
        void element.offsetWidth;
    }

    // Stable function reference, so removing first keeps this to one listener however often the
    // recovery path runs.
    element.removeEventListener('animationend', dropLedOn);
    element.addEventListener('animationend', dropLedOn, {once: true});
    element.classList.add('led-on');
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
    const lastSpeed = obj.lastSpeed ?? 0;
    const lastUpdateTime = obj.lastUpdateTime;
    const updateStr = lastUpdateTime ? `Updated: ${getTimeAgo(lastUpdateTime)}` : 'No update';

    // Dane do wyświetlenia - teraz każdy element to tablica [tekst, czyBold]
    const contentData = [
        [{ text: modelName, isBold: true }],  // nazwa statku - jeden element
        [{ text: "Speed:", isBold: false }, { text: `${Number(lastSpeed).toFixed(1)} kn`, isBold: true }],  // Speed: etykieta normalna + wartość pogrubiona
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

    // The tooltip layer is NOT one of the three the follow camera rotates, so its text was
    // never in danger of standing on its head — the counter-rotation the plan expected is
    // simply not needed, and that falls out of rotating layers instead of the stage.
    //
    // The ANCHOR is a different matter. `shipPos * stageScale` only inverts the stage's own
    // transform, which stopped describing the picture the moment the content layers gained
    // a rotation: while following, that arithmetic points at where the ship would be if the
    // chart had never turned. Asking Konva where the hull actually is, and converting that
    // screen point into this layer's coordinates (net transform is a plain translation by
    // the stage position, so subtracting it is the whole conversion), is right in both
    // views — north-up included, where it reduces to exactly the old expression.
    const shipOnScreen = konvaShipLayer.getAbsoluteTransform().point(obj.drawnPos);
    const px = shipOnScreen.x - konvaStage.x() + 10;
    const py = shipOnScreen.y - konvaStage.y() + 10;

    // Position each line of text.
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
        // After the ships exist: the toggles ask KonvaObjects whether a ship has reported.
        bindFollowToggles();
        bindOrientationButton();
        bindFollowChip();
        bindCompass();
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
