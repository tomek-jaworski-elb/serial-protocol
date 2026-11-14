---
paths:
  - "src/main/java/**/controller/web/**"
  - "src/main/java/**/resources/**"
  - "src/main/resources/templates/**"
  - "src/main/resources/static/js/**"
  - "src/main/resources/static/css/**"
  - "src/test/java/**/controller/**"
  - "docs/chart-map-view.md"
  - "docs/follow-mode-spec.md"
---

# Web surface, chart map and follow mode

Loaded when you touch the MVC controllers, Thymeleaf templates or the static JS/CSS. Invariant: hull geometry never depends on the live zoom (`CLAUDE.md`).

## Web surface
Two MVC controller families under `controller/web`:

- `MapController` — serial/realtime pages: `/` (`index`), `/terminal`, `/chart`, `/about`, `/greeting`, plus `/login`, `/logout`, `/name-service`, `/instructor-service`.
- `TracksController` — `/tracks` and its API, see "Track replay page" below.
- `CustomDBController` — all custom-domain CRUD; `ImageController` and `PdfReportController` round it out. `MyErrorController` + `WebExceptionHandler` handle errors.

`templates/fragment.html` is the shared layout — `head`, `header`, `footer`, `pagination(...)`, `toastNotifications`, `imageEditor(...)` fragments. Every page pulls in the footer, which is what makes the open-page count work. Menu entries behind `web.ui.extended=true` are gated in the template by comparing the property **as a string** (`@environment.getProperty(...) == 'true'`). `static/bootstrap-5-3-8` is the version actually referenced; `bootstrap-5-3-7` is a leftover.

## Chart map (`/chart`)
Full reference: [`docs/chart-map-view.md`](docs/chart-map-view.md)

A single Konva stage with four layers bottom-to-top: map image → tracks → ship polygons (clickable) → tooltip. Ships live in **map coordinates** (2666 × 4000, `MAP_WIDTH`/`MAP_HEIGHT`); zoom/pan transforms the whole stage, so calibration is resolution-independent. The map raster has three identically-sized variants loaded as a fallback chain (`.avif` ~109 KB → `.webp` ~143 KB → `.jpg` ~1 MB). Konva wipes its container on stage creation, so the zoom buttons must be siblings of `#konvaContainer`, never children.

Hull size is fixed in map coordinates (`SHIP_HULLS[id].scale` in `static/js/common/ship-geometry.js`, copied into `modelsConfig`) and must never be derived from the live stage scale. The tooltip layer, by contrast, is counter-scaled to keep a constant on-screen size.

### Follow mode
Full reference: [`docs/follow-mode-spec.md`](docs/follow-mode-spec.md). Toggling a ship in the list makes the camera hold that ship, with the chart turned so its course (COG, default) or heading (HDG) points up.

- **State lives in JS** (`followedShipId` in `chart-script.js`); the DOM only reflects it. Never read the mode back out of the markup.
- **The camera rotates three layers, not the stage**: map, tracks and ships. The tooltip layer is not rotated, so its text stays upright, but its anchor must come from `konvaShipLayer.getAbsoluteTransform()`.
- **Every ship's position and heading are eased together** over `chart.ship.position-smoothing-ms` (default 300, `0` restores teleporting) in *both* views, because easing only the followed ship would misplace it against the others, and easing only the position would show the new heading at the old place. `renderShipAt` places the hull and the LOD arrow from `drawnPos`/`drawnAngle`; `lastAngle` is the reported heading. The "Smooth movement" checkbox (`#smoothingToggle`) switches the easing for the open page: it starts from the property on every load and is never stored. A track's last vertex is the position the hull is *drawn* at, not the last one reported.
- **COG is only trusted above `chart.ship.cog-min-speed-kn`** (default 1.0); below it the camera falls back to HDG.
- While following, `clampStagePosition()` does not apply (the chart is rotated) and the anchor limit replaces it. `maxScale` is computed (`updateMaxScale()`), not a flat 3, and the north-up view inherits that ceiling.
- The two settings reach the browser as the `shipPositionSmoothingMs` and `cogMinSpeedKn` model attributes, read once at page render by `MapController` through `Resources`. They have env overrides `SHIP_POSITION_SMOOTHING_MS` and `COG_MIN_SPEED_KN`, and **changing them needs a restart**.

## Track replay page (`/tracks`)
`templates/tracks.html`, `static/js/tracks-script.js` (page, fetch, print) and `tracks-draw.js` (drawing), `static/css/tracks.css`. An A4-portrait Konva stage on the same map as `/chart`, using the shared `common/ship-geometry.js` and `common/map-image.js`; the hull invariant applies (hull size from the chart, never the zoom). Lines, dots and time labels keep a constant on-screen size, and take print sizes (mm at ~300 dpi) while exporting. The PDF is built by `TrackPdfService` from the exported image; Print opens the named window `tracksPdf` synchronously in the click (the form targets it), never after the async export, or Safari blocks it. Details: [`docs/track-replay.md`](docs/track-replay.md).
