// The chart map raster, shared by /chart and /tracks. Plain globals, loaded before the page script.

// Map coordinates: positions, hulls and tracks all live in this 2666 x 4000 space.
const MAP_WIDTH = 2666;
const MAP_HEIGHT = 4000;
// modern formats first, JPG as last-resort fallback
const MAP_IMAGE_SOURCES = [
    '/img/MapaSilm_2666x4000.avif',
    '/img/MapaSilm_2666x4000.webp',
    '/img/MapaSilm_2666x4000.jpg'
];

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
