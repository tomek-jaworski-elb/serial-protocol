// Two-finger pinch zoom on a Konva stage, shared by /chart and /tracks. Plain globals, loaded before the page script.

// zoomAt(pointer, newScale) is the page's own zoom and owns clamping and anchoring (/chart's follow mode anchors
// on the followed ship, not on the pinch point).
function bindStagePinch(stage, container, zoomAt) {
    let lastPinchDist = 0;
    // Native DOM listeners on `container`, not stage.on(): Konva does not reliably
    // dispatch its own 'touchstart'/'touchmove' bus events while a drag is active, so relying
    // on the Konva event bus here silently drops every pinch gesture that starts as (or
    // overlaps with) a single-finger drag — the normal way real pinch gestures begin.
    container.addEventListener('touchstart', (e) => {
        // A new two-finger touch always starts the pinch from a fresh distance, whatever an
        // interrupted gesture (a rotation with a finger down) left behind.
        if (e.touches.length >= 2) lastPinchDist = 0;
        if (e.touches.length >= 2 && stage.isDragging()) {
            stage.stopDrag();
        }
    }, {passive: true});

    container.addEventListener('touchmove', (e) => {
        const touch1 = e.touches[0];
        const touch2 = e.touches[1];
        if (!touch1 || !touch2) return;
        e.preventDefault();
        if (stage.isDragging()) {
            stage.stopDrag();
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
        zoomAt(pointer, stage.scaleX() * (dist / lastPinchDist));
        lastPinchDist = dist;
    }, {passive: false});

    container.addEventListener('touchend', () => {
        lastPinchDist = 0;
    }, {passive: true});
}
