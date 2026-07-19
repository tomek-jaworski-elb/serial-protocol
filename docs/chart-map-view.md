# Chart Map View — Implementation Notes

Documentation of the interactive lake map (`/chart`) implementation: rendering
architecture, image formats, zoom/pan controls, and ship position calibration.

Files involved:

| File | Role |
|---|---|
| `src/main/resources/templates/chart.html` | View template, Konva container, zoom control buttons |
| `src/main/resources/static/js/chart-script.js` | Konva stage, layers, WebSocket handling, zoom/pan/pinch |
| `src/main/resources/static/css/chart.css` | Container sizing, zoom controls overlay |
| `src/main/resources/static/img/MapaSilm_2666x4000.{avif,webp,jpg}` | Lake chart raster in three formats |

## Rendering architecture

The whole view is a single Konva stage sized to the viewport
(`.canvas-container`, 100% × 85vh), with the map image drawn as the bottom
layer. Layer order (bottom to top):

1. `konvaMapLayer` — `Konva.Image` with the lake chart, `listening: false`
2. `konvaTrackLayer` — ship track polylines, `listening: false`
3. `konvaShipLayer` — ship silhouette polygons (clickable)
4. `tooltipLayer` — ship info tooltip

Ships, tracks, and calibration triangles live in **map coordinates**
(2666 × 4000 px, constants `MAP_WIDTH` / `MAP_HEIGHT`). Zooming and panning
transform the whole stage, so ship positions and proportions relative to the
map never change.

Important: Konva clears the content of its container element on stage
creation — that is why the zoom buttons are siblings of `#konvaContainer`
inside `.map-wrapper`, not children of it.

## Map image formats

The map is loaded by `loadMapImage()` with a fallback chain:

1. `MapaSilm_2666x4000.avif` (~109 KB) — all modern browsers (Safari ≥ 16.4)
2. `MapaSilm_2666x4000.webp` (~143 KB) — Safari ≥ 14, legacy Android
3. `MapaSilm_2666x4000.jpg` (~1 MB) — last-resort fallback

All three variants have identical pixel dimensions (2666 × 4000), so the
coordinate calibration is format-independent. `chart.html` additionally
declares `<link rel="preload" as="image" type="image/avif">` — browsers
without AVIF support skip the preload based on the `type` attribute.

To regenerate the variants after updating the source JPG (requires Python
with Pillow ≥ 11):

```python
from PIL import Image
img = Image.open('MapaSilm_2666x4000.jpg')
img.save('MapaSilm_2666x4000.avif', quality=60, speed=4)
img.save('MapaSilm_2666x4000.webp', quality=75, method=6)
```

## Zoom, pan, and touch support

| Interaction | Implementation |
|---|---|
| Mouse wheel | `bindStageZoom()` — zoom towards the cursor position |
| Drag | stage `draggable: true` with `dragBoundFunc` clamping |
| Pinch (mobile) | `bindStagePinch()` — two-finger `touchmove` handling |
| Buttons `+` / `−` / reset | `bindMapControls()` — zoom to viewport center, reset restores the initial view |
| Double click / double tap | resets the view to the initial one |

Rules enforced by `clampScale()` and `clampStagePosition()`:

- initial view (and reset) = map fitted to the full container width; on
  portrait screens this shows nearly the whole map, on landscape the top part
  with vertical panning available;
- minimum scale = "whole map fits in the viewport" (recomputed on resize),
  maximum scale = 3× native map resolution;
- the map always covers the viewport when zoomed in, and is centered when
  zoomed out;
- `Konva.dragDistance = 3` so a small mouse jitter during a click is not
  interpreted as a drag (which would swallow ship clicks);
- `.canvas-container` has `touch-action: none` so the browser does not
  scroll/zoom the page while gesturing on the map.

## Ship silhouettes and calibration

Ship positions arrive over WebSocket (`/json`) in model coordinates and are
converted to map pixels by `getScaledPoints()` using the calibration constants
(`mapa_x = 2.407`, static shifts). **Do not change these constants** unless
the map raster itself changes — they map real-world positions to pixels on
the 2666 × 4000 chart.

Silhouettes are drawn with a constant scale (`modelsConfig[id].scale`) in map
coordinates, so their size always reflects the real ship size relative to the
map, at every zoom level. Do not add any zoom-dependent scaling here — the
true size ratio between ship and chart is a functional requirement.

The tooltip is counter-scaled (`tooltipLayer.scale = 1/stageScale`), so it has
a constant on-screen size at any zoom level.

## HTTP transfer optimizations

Configured in `application.properties`:

```properties
# Static resources HTTP caching (img, js, css)
spring.web.resources.cache.cachecontrol.max-age=7d
spring.web.resources.cache.cachecontrol.cache-public=true

# HTTP response compression (text resources)
server.compression.enabled=true
server.compression.mime-types=text/html,text/css,text/plain,application/javascript,application/json,image/svg+xml
server.compression.min-response-size=1024
```

Compression reduces Bootstrap CSS + JS and Konva from ~660 KB to ~160 KB per
first visit; the 7-day cache eliminates repeat downloads.

## Library versions

| Library | Version | Location |
|---|---|---|
| Konva | 10.3.0 | `static/js/konva-10_3_0/konva.min.js` |
| Bootstrap | 5.3.8 | `static/bootstrap-5-3-8/` |

Only the minified Konva build is shipped. When upgrading, download from
`https://cdn.jsdelivr.net/npm/konva@<version>/konva.min.js` and
`https://cdn.jsdelivr.net/npm/bootstrap@<version>/dist/`, place in a new
versioned directory, and update the references in `chart.html`
(Konva) and `fragment.html` (Bootstrap).

## Browser support

- Desktop: Chrome, Edge, Firefox, Safari — wheel zoom + drag pan.
- Mobile: pinch zoom + drag pan; the stage canvas is viewport-sized, so iOS
  Safari canvas size limits are not an issue (the previous implementation
  created a full-map-sized 2666 × 4000 stage, which could exceed them).
- Samsung Internet is blocked by an explicit alert in `chart-script.js`
  (`isSamsungBrowser()`).

Verified with Playwright (Chromium desktop 1280×900 and Pixel 7 emulation):
initial fit, cursor zoom, zoom buttons, position clamping, ship silhouettes,
tracks, and calibration triangle positions.
