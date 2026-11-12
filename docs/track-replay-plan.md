# Track replay (`/tracks`) — implementation plan

> Status: stages 1–7 implemented; stage 8 (captain names, 2026-11-11) implemented; stage 10 (readable buttons and replay, keyboard shortcuts, 2026-11-12) implemented; (stage 7, desktop browsers, 2026-11-11: popup-safe Print, automated rollover test; Safari and Edge await the owner's checklist). Reference: [`track-replay.md`](track-replay.md).
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
- **No file locks.** The server runs on Linux, where an open reader never blocks a rename. Files
  are still opened only through NIO (`Files.newInputStream` / `Files.lines`), which on Windows
  shares read, write *and delete*, so a developer running the app or the tests on Windows gets
  the same behaviour; `FileInputStream` and `RandomAccessFile` would block logback's rollover there.
- **Each stream is closed by try-with-resources** and lives only for one query; nothing is held
  open between requests.
- **Rollover races are tolerated, not prevented.** A file that disappears between listing and
  opening (`NoSuchFileException`), a half-written last line, or a `.gz` still being compressed by
  logback (`EOFException`) is skipped with a warning; the query returns what it could read.
- **Bounded load.** Queries run on request threads, never on the serial/UDP threads. A semaphore
  (`tracks.max-concurrent-queries`, default 2) caps parallel log scans; a request beyond it gets
  `503` at once instead of queuing. Lines are filtered while streaming, never loaded whole.
- **Tests:** an integration test drives frames through `FrameIngress` while a replay query streams
  the same tracking file and asserts every frame still reaches `/json` and the log, across a real
  logback size rollover with the reader open (automated, so it also guards a Windows dev machine).
  The same was checked by hand on Linux against the running app (stage 6).

### Page

| Topic | Decision |
|---|---|
| Language | English throughout (page and PDF), like the rest of the app. Dates `dd/MM/yyyy`. |
| Server | Linux only: Docker (JVM in UTC) or a plain JAR (host zone). |
| Clients | Desktop only (mouse, wheel), current Chrome, Edge, Firefox and Safari on Windows, macOS and Linux. Viewers sit in any zone; every time on the page and in the PDF is in the display zone, never the browser's. Tablets and phones: the page renders and pans, nothing more is promised. |
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
| Map | Konva 10, container fixed to **A4 portrait** (`aspect-ratio: 210 / 297`), wheel and button zoom, drag pan, full-scale nautical-mile scale bar bottom-left updated on zoom (position calibration 2.407 px/m). No on-map legend; the checkboxes are the legend. |
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
| Content | Header: range with zone (e.g. `11/11/2026 14:00:00 – 15:00:00 (Europe/Warsaw)`) and silhouette period; the image; colour legend; full-scale nautical-mile scale bar. |
| Delivery | Opens in a new tab, file name `tracks_<yyyy-MM-dd>_<HHmm>-<HHmm>.pdf`. |

### Shared geometry

Calibration, hull vertices and hull dimensions (`SHIP_HULLS`) move out of `chart-script.js` into
`static/js/common/ship-geometry.js`, shared by `/chart` and `/tracks`. `/chart` behaviour must not
change. Colours and names do not move: `/tracks` takes them from the server (`Models`), `/chart`
keeps its `ModelsOfShips` copy, as the `CLAUDE.md` ship-id invariant says.

## 3. Layout

```
service/tracks/
  TrackLogLineParser    one log line -> Optional<TrackPoint>, both formats
  TrackLogSource        available days, line stream for a log-zone day (.log + .gz)
  TrackQueryService     range + ship ids -> tracks + coverage; stateless, semaphore-bounded
  InvalidTrackQueryException, TrackQueryBusyException
dto/tracks/
  TrackPoint(time, x, y, heading), ShipTrack(modelId, points), TrackCoverage, TrackQueryResult, TrackPdfRequest
service/pdf/
  TrackPdfService       JPEG + metadata -> A4 PDF
controller/web/
  TracksController      GET /tracks, GET /tracks/api/days, GET /tracks/api/points, POST /tracks/pdf
static/js/
  common/ship-geometry.js, common/map-image.js
  tracks-script.js, tracks-draw.js
  css/tracks.css
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
   `docs/track-replay.md`; rollover under a reader checked on Linux against the running app
   (`maxFileSize` lowered through `--logging.config`, simulator feeding, queries every second).
   The Windows test first planned here was dropped on 2026-11-11: the server runs on Linux only.
7. **Desktop browsers.** Chrome, Edge, Firefox and Safari:
   - Print opens the PDF window synchronously in the click (named window, the form posts into it),
     because the JPEG is made asynchronously first and Safari/Firefox block a later `target=_blank`.
   - The ingest-under-query test covers a real size rollover with the reader open.
   - Verified in Chrome and Firefox here (headless); Safari and Edge by the owner from a short
     checklist in `docs/track-replay.md`.

Verification data: logs produced by the simulator until the owner supplies real tracking logs.

## 5. Known limits

- Silhouettes are drawn at 2 px/m while positions use 2.407 px/m, so hulls are about 17 % small
  (`docs/chart-map-view.md`). Kept as on `/chart`; the scale bar is in full-scale nautical miles, see §7.
- Docker logs written before `TRACKS_LOG_ZONE` is set are interpreted with that same setting; a
  host that changed its zone mid-history will show those days shifted.
- One source at a time is assumed. Serial and UDP both log through `FrameIngress` and the line
  does not name its source, so the same ship arriving by both paths would interleave two streams
  into a zigzag. No de-duplication is done; telling them apart would need a log-format change.
- History length is bounded by `TRACKING_LOG_TOTAL_CAP` as well as `maxHistory=365`; at full
  traffic the size cap is the one that bites.
- Drawing speed depends on the viewer's machine: with 24 h × 6 ships and the whole map in view,
  panning ran at ~10 fps in software-rendered headless Chrome; a GPU browser does better.

## 6. Stage 8 — captain names (added 2026-11-11)

Owner's request: next to a ticked ship a text field takes the first name and surname of the captain
whose track was recorded; the name is printed together with the ship's name.

Research (GOV.UK Design System checkboxes and names patterns, W3C "Personal names around the
world", WAI-ARIA APG, MDN, GDPR Art. 5/25) led to the simplest option: one plain field per ship,
no suggestions. Rejected: suggestions from the participants/trainers tables (the page is public, so
an endpoint would expose names to anonymous users, Art. 25(2), and captains are not modelled),
storing names with tracks on the server (there is no track table; retention and erasure duties for
something only the printout needs), and a `<datalist>` of earlier names (six fixed ships do not
need it; weak accessibility).

| Topic | Decision |
|---|---|
| Field | One text input per ship, a sibling of its row inside the `<li>` (not inside the label), shown only while the ship is ticked (`hidden` toggled; the checkbox carries `aria-controls`/`aria-expanded`). Focus is not moved. `maxlength="120"`, `autocomplete="off"`, `spellcheck="false"`, `aria-label="Captain of <ship>"`, placeholder "Captain (first and last name)". Several people may share the field ("Jan Kowalski, Anna Nowak"). |
| Lifetime | Never stored: not in localStorage, not on the server, not logged. A page reload clears the names; Refresh, Clear, unticking and re-ticking keep what was typed. |
| Validation | No character restrictions (Unicode letters, hyphens, apostrophes, diacritics). Server normalises: NFC, control characters removed, whitespace collapsed, trimmed; blank = no captain. Over 120 characters after normalising, or a captain for a ship that is not printed, -> 400 like the other print errors. |
| Transport | `printTracks` reads the fields at Print time for the ships it prints (those with points) and posts one extra form field `captains`, a JSON object `{"1":"Jan Kowalski"}` (a comma list would split names). |
| Printout | Legend entry "Warta — Jan Kowalski" (em dash), colour swatch unchanged. Never truncated: an entry too wide for half the row takes the full row and wraps onto further lines. The server lays the legend out first and fits the map image into the space left, keeping the A4 ratio, so the page stays one A4 sheet (worst case, measured: six 120-character names make the legend ~6 cm deep, so the map prints ~18 cm tall at ~230 dpi instead of ~23.5 cm at ~300). The browser export is unchanged. A ticked ship without points is not in the legend, so neither is its captain. |
| Tests | TracksPageTest (field per ship, attributes, `captains` in the print form); TracksControllerTest (Polish names -> 200, malformed JSON -> 400, unknown ship -> 400); TrackPdfServiceTest (legend text with diacritics, wrapping of a 120-character name, six long names still one A4 page, normalisation, refusals). Reveal and print checked in Chrome and Firefox. |

## 7. Stage 9 — scale bar (2026-11-11)

The first scale bar labelled real lake metres (1/2/5 x 10^n m) and was wrong. The chart is a 1:24 model chart:
its own bar "0 Nm ... Nm 1" (top-left, 185 px between ticks in the 2666 x 4000 raster) is a **full-scale**
nautical mile, 1852 / 24 = 77.17 lake metres = 185.7 px at `mapa_x = 2.407` (measured on the raster, and
consistent with the Ilawa centre's 1:24 models and the app's reference points). `mapa_x` is correct and unchanged.

Decision: the bar (screen and PDF) is in full-scale nautical miles only, steps 1/2/5 x 10^n Nm ("0.05 Nm", "1 Nm"),
no metre bracket; the screen bar has a tooltip. `MODEL_SCALE = 24` and `NM_METRES = 1852` live in
`static/js/common/ship-geometry.js`; the page posts `widthNm` (area width / `mapa_x` x 24 / 1852) so Java holds
no calibration.

## 8. Stage 10 — readable buttons and replay (2026-11-12)

Owner's request: make the panel buttons more readable, following current practice, and add a replay of the ships'
runs over the drawn range: play, stop, clean, and a speed slider x1–x10 in steps of 0.2.

Research (WCAG 2.2 1.4.11/2.5.8, Material 3 and Bootstrap 5.3 button hierarchy, WAI-ARIA APG slider and toggle
button; replay in MarineTraffic, ship-handling simulator debriefs, deck.gl TripsLayer, kepler.gl, Cesium, Strava)
found: one primary action per group, destructive actions marked, icons with labels in panels, readable disabled
states; replay = all vessels on one shared clock, full track faded, played part in full colour, one moving hull per
vessel, clock readout and a scrubber.

| Topic | Decision |
|---|---|
| Groups | Headings like the other panel sections: *Data* [Refresh][Clear], *Replay* [Play/Pause][Stop][Reset] + speed + scrubber + clock, *Print* [Print][Print frame][Remove frame]. |
| Hierarchy | Refresh `btn-primary` (only filled one in Data); Clear `btn-outline-danger`; Play `btn-success`; Stop, Reset, Print, Remove frame `btn-outline-secondary`; Print frame `btn-outline-primary`, filled while on (`aria-pressed`). Default size (no `btn-sm`). |
| Icons | About ten Bootstrap Icons paths (MIT) as an inline SVG sprite in `tracks.html` (`<symbol>` + `<use>`, `currentColor`, `aria-hidden`), licence noted in a comment; no font, no new vendored folder. Icon + label for Refresh, Clear, Print, Print frame, Remove frame; icon only with `aria-label` and `title` for Play/Pause, Stop, Reset. Map zoom buttons keep the `/chart` look and gain `aria-label`s. |
| Disabled | Higher contrast than Bootstrap's 0.65 opacity and a dashed outline, plus one muted hint "Pick ships and a range, then Refresh" while nothing is drawn. |
| Replay range | The data of the last Refresh (first to last point of the drawn tracks); editing From/To without Refresh does not affect it. |
| Speed | `x1` = real time (1 s of track per second); slider 1–10 step 0.2, shown as "x2.4" with the total replay duration ("x2.4 · 25 min"); remembered with the other settings. |
| Play/Pause | One toggle (`aria-pressed`, icon swap). First press starts at the range start; later presses pause and resume; after the end, Play starts again from the start. |
| Stop | Halts and rewinds to the start: faded tracks, hulls at their start positions, clock at the start. |
| Reset | Ends the replay, removes its overlay (trail, moving hulls, clock state) and restores the static view; data stays. Named "Reset" so it is not confused with the panel's Clear. |
| Scrubber | A range input over the replay range, step 1 s, moved by the clock while playing; dragging it seeks (also while playing). Keyboard per APG: arrows ±1 s, Page Up/Down ±1 min, Home/End. Clock readout `dd/MM/yyyy HH:mm:ss` in the display zone. |
| While replaying | Full tracks faded, the played part in full colour, one hull per ship at the clock time (position interpolated within a segment, heading turned the short way), hidden before its first point, after its last and inside a gap longer than the gap field. Static silhouettes and their time labels are hidden and return on Reset. Zoom, pan and the print frame keep working. Hull size from the chart, never from the zoom. |
| End | Stops on the last frame. |
| Background tab | The browser pauses animation frames; each frame step is capped at 250 ms, so returning does not jump. |
| Refresh / Clear | End the replay (as Reset) before replacing or clearing data. |
| Print | Always the full static view, whatever the replay state. |
| Server | No change. |

### Stage 10 follow-ups (2026-11-12)

| Topic | Decision |
|---|---|
| Replay bar | The replay controls leave the panel: a bar directly under the map, video-player style — [Play/Pause][Stop][Reset] · scrubber · clock · speed (with the duration). The map keeps its A4 ratio and shrinks by the bar's height so map + bar fit the viewport like the map alone did; the panel keeps Ships, Time range, Silhouettes, Data and Print. Nothing overlaps the map. |
| Keyboard shortcuts | Media-player and map conventions: Space or K play/pause; S stop; J / L −10 s / +10 s; Home / End start / end; on the focused scrubber ←/→ ±1 s and PgUp/PgDn ±1 min; `<` / `>` speed −0.2 / +0.2; `+` (or `=`) / `-` zoom; `0` whole map; F print-frame mode; Esc leaves frame mode, otherwise Reset; `?` opens a "Keyboard shortcuts" dialog. Matched on `event.key` (works on the Polish layout); ignored with Ctrl/Alt/Meta (AltGr included), during IME composition, in text/number inputs, selects, the open Flatpickr calendar and the dialog; Space is left to focused buttons and checkboxes, arrows/Home/End to focused range inputs; toggles ignore key repeat. Actions click the existing buttons, so disabled states hold. Keys appear in button titles and `aria-keyshortcuts`. No Refresh/Print keys (Ctrl+P stays the browser's), no map panning by arrows. |
| WCAG 2.1.4 | Single-character shortcuts can be switched off: a checkbox in the shortcuts dialog, remembered in localStorage (try/catch), default on; non-character keys (Esc, arrows, Home/End, PgUp/PgDn) stay active. Every action keeps an on-screen button. |
| Static resources | Spring resource chain with content versioning (`spring.web.resources.chain.strategy.content.enabled=true`, `paths=/**`) so every `@{...}` asset URL carries a content hash and a browser never mixes new HTML with week-old cached CSS/JS. App-wide; all pages checked. Inline SVG icons also carry `width`/`height` so they never blow up without CSS. |
| Replay bar layout | Two rows so the scrubber gets the map's width: [Play/Pause][Stop][Reset] + scrubber, then clock + speed with its duration. The bar's buttons are `btn-sm` (exception to "default size": a compact player bar); the panel buttons stay default size. |
| Silhouette cap | Holds in every case: when even 60 s exceeds 5 000 (e.g. a gap threshold below the sampling interval makes every point a segment end), the segment-end extras are dropped, leaving at most one silhouette per ship per 60 s. |
| Log format guard | `IngestDuringTrackQueryTest` builds its tracking appender from the production `logback-spring.xml` (line pattern, file, archive naming), so changing any of them turns it red. The API's coverage carries `unreadableLines` (track lines in the range that did not parse) and the page warns "N log lines in this range could not be read — the log format may have changed." instead of only reporting missing data. |
