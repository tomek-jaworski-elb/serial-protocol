# Track replay (`/tracks`) — implementation plan

> Status: agreed 2026-11-11 (design closed after a Q1–Q31 review), not started.
> The existing `/tracks` implementation may be replaced entirely.

## 1. Requirements (owner)

1. Draw the tracks of several ship models at once, chosen from a list.
2. Choose the time range with `HH:mm:ss` precision.
3. Choose the day(s) from the recorded history.
4. A4 map with zoom; the hull silhouette keeps its size relative to the chart at every zoom;
   the print area is chosen manually with the mouse.
5. Once drawn: **Clear** (empties the view), **Refresh** (redraws), **Print** (PDF ready to print).
6. A slider sets the period, in seconds, at which silhouettes are drawn; every track point is
   marked on the map and consecutive points are joined by lines.
7. Using the replay must never stop or delay the logging of new frames.

## 2. Decisions

### Data and server

| Topic | Decision |
|---|---|
| Storage | Still the tracking log, no table. The `Translated message: ModelTrackDTO(...)` line stays byte-for-byte as it is (it is an API, see `CLAUDE.md`). |
| Log formats | The parser accepts both the pre-CRC line (`...tugDirection=X))`) and the current one (`..., isCRCValid=true)`). Lines with `isCRCValid=false` are skipped. Scientific notation is accepted. Lines that look like `Translated message` but do not parse are counted and logged as one warning per query; the user is not told. |
| Sources | `logs/tracking.log` plus the archives `logs/yyyy/MM/tracking_dd-MM-yyyy.N.log.gz`, read as streams. The date always comes from the line, never from the file name. |
| Time zones | The app runs both in Docker (JVM in UTC) and as a plain JAR. `tracks.log-zone` is the zone the log timestamps were written in (default: system zone; `docker-compose.yml` sets `TRACKS_LOG_ZONE=UTC`). `tracks.display-zone` (default `Europe/Warsaw`) is the only zone the user sees: pickers, labels, PDF. The browser zone is ignored. |
| Range | Date-time *from* and *to* (`dd/MM/yyyy HH:mm:ss`), may cross midnight, **at most 24 h**, `from < to`, not entirely in the future. Checked in the browser (message under the fields, Refresh disabled) and again on the server (`400`). |
| Volume | No decimation: up to ~500 k points for 24 h × 6 ships. Compact JSON, points as `[t, x, y, hdg]` arrays, gzip on. Revisit only if real logs prove too slow. |
| Response | `200` always for a valid query, with points per ship **and coverage**: whether any log file covers the range, the recorded history bounds, and the first/last time actually found. Empty is not an error. |
| Retention | `totalSizeCap` of `SAVE-TRANSMISSION-FILE` becomes configurable (`TRACKING_LOG_TOTAL_CAP`, default **1GB**, ~2 months at full 6-ship traffic, ~15–20 MB/day gzipped). Only rotation and deletion change; the line format and the writer do not. Separate commit in stage 1, documented with the per-day estimate. |
| Access | Public, like `/chart`. CSRF stays on: the PDF `POST` sends the token from a `<meta>` tag. |

### Ingest must never stop

Drawing tracks must not delay, block or lose a single frame of live ingest or its tracking log.

- **Read-only, no changes on the write side.** `MessageTranslator` and the ingest pipeline are
  not touched; `logback-spring.xml` changes only the retention cap above.
- **No file locks.** Files are opened only through NIO (`Files.newInputStream` / `Files.lines`),
  which on Windows shares read, write *and delete*. `FileInputStream` and `RandomAccessFile` are
  forbidden here: without delete-sharing, logback's midnight/size rollover cannot rename
  `tracking.log` while a query holds it, and logging keeps going into the wrong file.
- **Each stream is closed by try-with-resources** and lives only for one query; nothing is held
  open between requests.
- **Rollover races are tolerated, not prevented.** A file that disappears between listing and
  opening (`NoSuchFileException`), a half-written last line, or a `.gz` still being compressed by
  logback (`EOFException`) is skipped with a warning; the query returns what it could read.
- **Bounded load.** Queries run on request threads, never on the serial/UDP threads. A semaphore
  (`tracks.max-concurrent-queries`, default 2) caps parallel log scans; a request beyond it gets
  `503` at once instead of queuing. Lines are filtered while streaming, never loaded whole.
- **Tests:** an integration test drives frames through `FrameIngress` while a replay query streams
  the same tracking file and asserts every frame still reaches `/json` and the log. Rollover
  under a reader on Windows is checked by hand (stage 6).

### Page

| Topic | Decision |
|---|---|
| Language | English throughout (page and PDF), like the rest of the app. Dates `dd/MM/yyyy`. |
| Devices | Desktop only (mouse, wheel). On a phone the page renders and pans, nothing more is promised. |
| Menu | `Tracks` in the main bar right after `Chart`, desktop and mobile variants of `fragment.html`. |
| Ships | Checkboxes for all 6 models with colour swatch and name, built from `Models`. None selected by default. After a draw, a ship with no points gets a "no data" note. |
| Defaults | Range = last hour up to now (display zone). Nothing is drawn until Refresh. |
| Gap | Line breaks on a gap longer than the threshold. Default from `tracks.gap-seconds=10`, editable on the page (1–600 s); a change redraws without refetching. GPS jumps are not filtered. |
| Silhouette slider | 0–60 s, step 1; **0 = a silhouette at every point**. For N > 0, silhouettes at instants **aligned to the clock** (multiples of N s since midnight), at the nearest recorded point, with its heading. **Always** one on the first and last point of every continuous segment. |
| Silhouette limits | At most **5 000 silhouettes** per drawing (all ships, first/last-of-segment ones included). Above that the slider's minimum is raised to the smallest period that fits, with a message such as "Period raised to 12 s: too many silhouettes for this range". Dots and lines still show every point. |
| Silhouette matching | A clock-aligned instant gets a silhouette only if the nearest point lies within **min(N/2, 2 s)** of it; otherwise the instant is skipped (gaps do not pile silhouettes on the segment ends). A point carries at most one silhouette. The time label shows the point's **real** time, not the aligned instant. |
| Silhouette toggles | "Show silhouettes" (default on) hides the layer. "Time labels" (default off, disabled for a period below 10 s) puts `HH:mm:ss` next to each silhouette. Slider and toggles redraw at once, no refetch. |
| Silhouette look | Outline in the ship colour, fill in the ship colour at ~25 % opacity. Size in map units from the shared hull geometry, never from the live zoom. |
| Dots, lines, labels | Constant on-screen size (lines and dots like the track on `/chart`). Dots of one ship are one `Konva.Shape` with a custom `sceneFunc`. No hover tooltip. |
| Map | Konva 10, container fixed to **A4 portrait** (`aspect-ratio: 210 / 297`), wheel and button zoom, drag pan, metric scale bar bottom-left updated on zoom (position calibration 2.407 px/m). No on-map legend; the checkboxes are the legend. |
| Print frame | Toggle button "Print frame": dragging draws an A4-portrait frame, then the mode switches off. One frame, movable and resizable by handles (Konva `Transformer`, ratio locked); drawing again replaces it; "Remove frame" deletes it. |
| Refresh | Fetches again with the current form and redraws everything; also does the first draw. While loading: spinner on Refresh, Refresh/Clear/Print disabled, the previous drawing stays until new data arrives. Manual only, no auto-refresh. |
| Clear | Clears the drawing only; form, frame, zoom and position stay. Print is disabled until the next draw. |
| Empty result | All selected ships empty: drawing cleared, Print disabled, a message by cause: "No positions for the selected ships between … and …. Logs for this period exist." / "No logs for this period. Recorded history: … – …." / future range (caught in the browser). Data only in part of the range: drawn, plus "Data found HH:mm:ss – HH:mm:ss". |
| Remembered settings | `localStorage` keeps the ship selection, silhouette period, both toggles and the gap threshold (every access wrapped in try/catch; defaults when storage is empty or blocked). Range, print frame, zoom and position always start fresh. |
| Errors | `503`: "The server is busy with another track query, try again in a moment.", no automatic retry. Other errors: the message is shown and the previous drawing stays. |

### Print

| Topic | Decision |
|---|---|
| Area | The print frame; **no frame → the current view**. |
| Quality | Fixed ~300 dpi regardless of the screen zoom; the frame may lie partly off screen. Map raster is 2666 × 4000, so the background softens on a small frame; vector content stays sharp. |
| Sizes on paper | Line ~0.3 mm, dot ~0.8 mm diameter, time label ~2 mm high. |
| Pipeline | The browser renders the area with `stage.toDataURL({mimeType: 'image/jpeg', quality: 0.92, pixelRatio})` (~2–3 MB for 2480 × 3508 px; a PNG would approach the global 10 MB multipart limit, which stays as it is) and posts it with the metadata; `TrackPdfService` (OpenPDF, DejaVu fonts) builds one A4 portrait page. |
| Content | Header: range with zone (e.g. `11/11/2026 14:00:00 – 15:00:00 (Europe/Warsaw)`) and silhouette period; the image; colour legend; metric scale bar. |
| Delivery | Opens in a new tab, file name `tracks_<yyyy-MM-dd>_<HHmm>-<HHmm>.pdf`. |

### Shared geometry

Calibration, hull vertices, hull dimensions and colours move out of `chart-script.js` into
`static/js/common/ship-geometry.js`, shared by `/chart` and `/tracks`. `/chart` behaviour must not
change.

## 3. Layout

```
service/tracks/
  TrackLogLineParser    one log line -> Optional<TrackPoint>, both formats
  TrackLogSource        available days, line stream for a log-zone day (.log + .gz)
  TrackQueryService     range + ship ids -> tracks + coverage; stateless, semaphore-bounded
  TrackPdfService       PNG + metadata -> A4 PDF
dto/tracks/
  TrackPoint(time, x, y, heading), ShipTrack(modelId, points), TrackCoverage, TrackPdfRequest
controller/web/
  TracksController      GET /tracks, GET /tracks/api/days, GET /tracks/api/points, POST /tracks/pdf
static/js/
  common/ship-geometry.js
  tracks-script.js
templates/tracks.html
```

Removed: `ReadLogsFileService`, `LogPatternMatcher`, the current `TrackService`, `LogItem`,
`ModelTrackJSDTO`, `CheckBoxOption`, both `/tracks` handlers in `MapController`, the
`POST /tracks` rule in `SecurityConfig`, `common-draw-ship.js`, and their tests.

## 4. Stages

Branch `track-replay` from `master`. After each stage the result is shown (tests; from stage 3
also a screenshot driven by the simulator) and committed only after the owner accepts it. One PR
at the end.

1. **Data.** Parser, log source, query service with coverage and the semaphore, JSON API,
   properties. Tests: both line formats and rejected lines; a temp `logs/` tree with `.log` and
   `.gz` parts; range edges to the second, midnight crossing, zone conversion, the 24 h limit;
   empty-result coverage cases; ingest-under-query integration test; MockMvc on the API.
   Separate commit: configurable `TRACKING_LOG_TOTAL_CAP` (default 1GB) with its documentation.
2. **Shared geometry.** Extract `ship-geometry.js`, load it on `/chart`, point
   `ChartShipListTest#theHullIsSizedFromTheChartAndNotFromTheZoom` at it, update the
   "ship ids live in three places" invariant in `CLAUDE.md` and `AGENTS.md`.
3. **Page and map.** Ship checkboxes; Flatpickr date-time pickers with seconds, only recorded
   days enabled; range validation; A4 Konva stage with zoom, pan, scale bar; print frame mode.
4. **Drawing.** Segments, dots, clock-aligned silhouettes with tolerance, de-duplication and the
   5 000 cap, toggles, gap field, remembered settings; Clear, Refresh, loading state,
   empty-result and error messages.
5. **Print.** Export at ~300 dpi, `POST /tracks/pdf`, PDF in a new tab. Test: PDFBox reads one A4
   page with an embedded image.
6. **Release.** Menu entry after `Chart`; a new `TracksPageTest` (keeps the "every asset exists"
   check); update `.claude/rules/ingest-pipeline.md` § Track replay and `web-chart.md`; write
   `docs/track-replay.md` including the **manual Windows rollover test** (run the JAR, feed it
   with `run-sim.bat replay ... --loop`, temporarily lower `maxFileSize`, query tracks during
   rollovers, check that `.gz` archives appear and `tracking.log` keeps growing). The owner runs
   it; stage 6 is not closed without the result.

Verification data: logs produced by the simulator until the owner supplies real tracking logs.

## 5. Known limits

- Silhouettes are drawn at 2 px/m while positions use 2.407 px/m, so hulls are about 17 % small
  (`docs/chart-map-view.md`). Kept as on `/chart`; the scale bar follows the position calibration.
- Docker logs written before `TRACKS_LOG_ZONE` is set are interpreted with that same setting; a
  host that changed its zone mid-history will show those days shifted.
- One source at a time is assumed. Serial and UDP both log through `FrameIngress` and the line
  does not name its source, so the same ship arriving by both paths would interleave two streams
  into a zigzag. No de-duplication is done; telling them apart would need a log-format change.
- History length is bounded by `TRACKING_LOG_TOTAL_CAP` as well as `maxHistory=365`; at full
  traffic the size cap is the one that bites.
