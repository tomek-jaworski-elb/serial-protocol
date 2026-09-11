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

## Follow mode

Selecting a ship in the list switches the chart into a following view: the camera holds that ship,
the chart turns so its course (or heading) points up, and the zoom is set so the hull is a quarter
of the viewport height. Full reference: [`docs/follow-mode-spec.md`](follow-mode-spec.md).

Three things in this document change while that mode is on, so read them together:

- **`maxScale` is no longer a flat 3.** The follow optimum asks for 4.3× to 12.6× depending on the
  ship and the viewport, so the ceiling is computed rather than fixed — and because leaving the
  mode keeps your zoom, **the ordinary north-up view inherits that range too**. Above roughly 3×
  the raster is visibly soft; that is accepted, not a defect.
- **`clampStagePosition()` does not apply while following.** Its "the chart must cover the viewport"
  rule describes an axis-aligned chart, and the chart is rotated. The anchor limit takes its place.
- **Hull positions are eased, not applied straight.** Messages arrive about once a second, so every
  silhouette slides to its newest reported position over `chart.ship.position-smoothing-ms`
  (default 300, `0` restores the original teleporting behaviour). Every ship is eased, never just
  the followed one, so the distances between them stay truthful.

## Zoom, pan, and touch support

| Interaction | Implementation |
|---|---|
| Mouse wheel | `bindStageZoom()` — zoom towards the cursor position |
| Drag | stage `draggable: true` with `dragBoundFunc` clamping |
| Pinch (mobile) | `bindStagePinch()` — two-finger `touchmove` handling |
| Buttons `+` / `−` / reset | `bindMapControls()` — zoom to viewport center. Reset restores the initial view, except while following, where it restores the camera instead (optimal zoom, ship re-centred) |
| Double click / double tap | resets the view to the initial one; while following it leaves the mode first, then fits |

Rules enforced by `clampScale()` and `clampStagePosition()`:

- initial view (and reset) = map fitted to the full container width; on
  portrait screens this shows nearly the whole map, on landscape the top part
  with vertical panning available;
- minimum scale = "whole map fits in the viewport" (recomputed on resize). **While
  following, the floor is instead the zoom at which silhouettes stop degrading to LOD
  arrows** (`FOLLOW_MIN_SCALE`);
- maximum scale is **no longer a flat 3×**: it is computed from the most demanding ship's
  follow optimum (`updateMaxScale()`), which puts it between roughly 6× and 19× depending
  on the viewport. Leaving follow mode keeps your zoom, so the ordinary north-up view can
  reach that range too;
- the map always covers the viewport when zoomed in, and is centered when
  zoomed out — **except while the chart is rotated**, where `clampStagePosition()` steps
  aside entirely (its arithmetic assumes an axis-aligned chart) and the follow mode's
  anchor limit takes over;
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
coordinates, so a ship keeps the same size relative to the chart at every zoom
level. **Do not add any zoom-dependent scaling here** — a ship that grows and
shrinks against the chart it sits on is the thing this rule exists to prevent,
and it is a functional requirement. `ChartShipListTest`
(`theHullIsSizedFromTheChartAndNotFromTheZoom`) parses this rule out of
`chart-script.js` and fails if hull geometry is ever built from the live stage
scale — the temptation is real, because the LOD arrow four lines away is
deliberately given a constant on-screen size.

What this paragraph used to claim, and should not have: that the silhouette
reflects the ship's *real* size. It does not. Hulls are drawn at `cfg.scale`,
which is 2 px per metre, while positions are placed with `mapa_x = 2.407` px
per metre — so every silhouette is about **17% shorter and narrower** than the
chart's own scale would make it. The ratio is constant, which is what the rule
above is really about, but it is not 1:1 with the world. Left alone
deliberately: correcting it means touching calibration, and
`docs/follow-mode-spec.md` measures its "ship length = 25% of viewport height"
against what is actually drawn.

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
| Konva | 10.3.2 | `static/js/konva-10_3_2/konva.min.js` |
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
