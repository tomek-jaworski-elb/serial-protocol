// Ship geometry shared by /chart and /tracks, so both draw the same hulls and place
// positions with the same calibration. Plain globals, loaded before the page script.

const mapa_x = 2.407; /// było 2.4   = kalibracja mapy

// Hull dimensions per ship id (same ids as Models and MessageTranslator.MODEL_MAP).
// shipParams = [length, width, pp] for computeShipVerticesForKonva.
const SHIP_HULLS = Object.freeze({
    1: { scale: 2, shipParams: [12.21, 2, 0] },
    2: { scale: 2, shipParams: [13.78, 2.38, 0] },
    3: { scale: 2, shipParams: [11.55, 1.8, 0] },
    4: { scale: 2, shipParams: [15.5, 1.79, 0] },
    5: { scale: 2, shipParams: [10.98, 1.78, 1] },
    6: { scale: 2, shipParams: [16.43, 2.23, 0] },
});

// Telemetry position (metres) to map pixels.
function getScaledPoints(oldX, oldY) {
    const staticShift_y = 506;
    const staticShift_x = 64;
    const scaleX = mapa_x;
    const scaleY = mapa_x;
    const y = (-oldX + staticShift_y) * scaleY;
    const x = (oldY + staticShift_x ) * scaleX;
    return {x, y};
}

const ANGLE_CORRECTION = 9; // zgodnie z Twoim oryginalnym kodem

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
