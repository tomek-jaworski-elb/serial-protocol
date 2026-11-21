# Track replay (`/tracks`)

Draws the recorded tracks of the ship models on the lake chart for a chosen time range and prints
them to an A4 PDF. Design decisions and stages: [`track-replay-plan.md`](track-replay-plan.md).

## What it does

- Pick ships (colour swatch + name), a range `dd/MM/yyyy HH:mm:ss` (at most 24 h, may cross midnight,
  not in the future; only recorded days are selectable) and press **Refresh**.
- Every point is a dot, consecutive points are joined by lines; a line breaks on a gap longer than
  the gap threshold. Hull silhouettes are drawn at a chosen period.
- **Clear** empties the drawing, **Print** makes the PDF (page area: the print frame, or the current
  view when there is none).
- A ticked ship shows a **Captain** field (max 120 characters). It is never stored: not in `localStorage`,
  not on the server, not logged. A reload clears it; Refresh, Clear and unticking keep it.
- Nothing here writes to the logs or touches ingest.

## Architecture

| File | Role |
|---|---|
| `service/tracks/TrackLogSource` | Lists recorded days; streams the active `logs/tracking.log` and the archives `logs/yyyy/MM/tracking_dd-MM-yyyy.N.log.gz`. NIO only, so logback can still roll the file. A vanished file, a half-written line or a `.gz` still being compressed is skipped with a warning. |
| `service/tracks/TrackLogLineParser` | One `Translated message: ModelTrackDTO(...)` line to a point; both formats (before and with `isCRCValid`); `isCRCValid=false` is skipped. The date comes from the line. |
| `service/tracks/TrackQueryService` | Range and ships to tracks plus coverage; display zone to log zone; at most 24 h; a semaphore bounds parallel scans. |
| `controller/web/TracksController` | `GET /tracks`, `GET /tracks/api/days`, `GET /tracks/api/points`, `POST /tracks/pdf`. Public, like `/chart`. |
| `service/pdf/TrackPdfService` | The posted image plus metadata to one A4 portrait page (OpenPDF). |
| `configuration/TracksProperties` | The `tracks.*` settings. |
| `templates/tracks.html`, `static/js/tracks-script.js`, `tracks-draw.js`, `static/css/tracks.css` | Page, Konva stage, drawing, export. |
| `static/js/common/ship-geometry.js`, `map-image.js`, `stage-pinch.js` | Hull geometry, map loading and two-finger pinch, shared with `/chart`. |

The log format is the storage format: rewording `MessageTranslator`'s log line breaks replay.

## API

`GET /tracks/api/days`

```json
{"days": ["2026-11-10", "2026-11-11"], "displayZone": "Europe/Warsaw"}
```

`GET /tracks/api/points?from=2026-11-11T14:00:00&to=2026-11-11T15:00:00&models=1&models=6`
(display-zone local times; `models` are ship ids).

```json
{"ships": [{"id": 1, "points": [[1762866000123, 1304.5, 2210.0, 87.5]]}],
 "coverage": {"logsFound": true, "historyFrom": "2026-11-10", "historyTo": "2026-11-11",
              "firstPoint": 1762866000123, "lastPoint": 1762869599876, "unreadableLines": 0}}
```

A point is `[epoch millis, x, y, heading]`, x/y in lake metres as the frames carry them (the page maps them
with `getScaledPoints`). An empty result is still `200`; `coverage` tells why, and `unreadableLines` counts
track lines in the range that did not parse — non-zero means the log format changed, and the page says so. Errors: `400` invalid range or parameters (`{"message": ...}`), `503` when
`tracks.max-concurrent-queries` scans already run.

`POST /tracks/pdf` (multipart: `image`, `from`, `to`, `silhouettes`, `ships`, `widthNm`) returns
`application/pdf`; `400` is plain text.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `tracks.log-zone` / `TRACKS_LOG_ZONE` | system zone; `UTC` in `docker-compose.yml` | Zone the log timestamps were written in. |
| `tracks.display-zone` | `Europe/Warsaw` | The only zone the user sees (pickers, labels, PDF). |
| `tracks.gap-seconds` | `10` | Line breaks on a longer gap; editable on the page (1-600). |
| `tracks.max-concurrent-queries` | `2` | Parallel scans; the next gets `503`. |
| `TRACKING_LOG_TOTAL_CAP` (`logging.tracking.total-size-cap`) | `1GB` | Size cap of all tracking archives; about 15-20 MB/day gzipped at full six-ship traffic, so about two months. `maxHistory` is 365 days. |

## Page behaviour

- Silhouettes: period 0-60 s; 0 is one at every point. Otherwise they sit at clock-aligned instants
  on the nearest point (within min(N/2, 2 s)), plus the first and last point of each segment.
  At most 5000 per drawing: above that the minimum period is raised and a message says so.
- Messages say why a result is empty (no logs, no positions, future range) and when data covers only
  part of the range; `503` and other errors keep the previous drawing.
- The stage follows `.canvas-container` through a `ResizeObserver`, not `window` `resize`. After a resize or rotation
  the whole-map view stays the whole map; a zoomed view keeps its centre (clamped to the new minimum zoom, so a
  rotation to a smaller map can end as the whole map).
- On the stacked layout the panel is below the map, so Refresh scrolls its result into view (`revealResult()`): after
  a plain result the replay bar, stopped just above the bottom nav (`scroll-margin-bottom`); after a warning or error
  the message above the map.
- Remembered in `localStorage`: ships, period, toggles, gap, replay speed. Range, frame, zoom and position start fresh.
- Print: ~300 dpi JPEG of the area, line about 0.3 mm, dot about 0.8 mm, label about 2 mm; header with
  range and zone, colour legend and scale bar in full-scale nautical miles (`widthNm`: the area width in Nm of the 1:24 chart). Opens in a new tab.
- Captains go in the form field `captains` (JSON `{"1":"Jan Kowalski"}`, printed ships only). The server
  normalises them (NFC, control characters removed, whitespace collapsed) and refuses over 120 characters
  or an unprinted ship. The legend reads "Ship — Captain"; an entry wider than half the row takes a row
  and wraps, never truncated. The legend is laid out first and the image is fitted into the height left.

## Buttons

Panel sections *Data* (Refresh primary, Clear outline-danger), *Replay* and *Print*, default-size Bootstrap
buttons with Bootstrap Icons (MIT) from an inline SVG sprite in `tracks.html`. Disabled buttons are half-opaque with
a dashed outline, and a hint says what to do while nothing is drawn. `#pdfForm` sits right after the header: its
CSRF token creates the session, which fails once the response buffer (8 KB) is committed, so nothing large may come before it.

## Keyboard shortcuts

One `keydown` listener in `tracks-script.js` reads the `SHORTCUTS` table, which also fills the help dialog (`?` or the
panel's *Keyboard shortcuts* button). Keys are matched on `event.key`; Ctrl/Alt/Meta, IME composition, text fields,
selects, the Flatpickr calendar and the dialog are ignored. Single-character keys can be switched off in the dialog
(localStorage `tracks.shortcuts`, default on; WCAG 2.1.4). Esc, Home and End stay active.

| Key | Action |
|---|---|
| Space, K | Play / pause (Space is left to a focused button or checkbox) |
| S | Stop |
| J / L | Back / forward 10 s |
| Home / End | Start / end of the replay |
| < / > | Speed -0.2 / +0.2 |
| + (or =) / - / 0 | Zoom in / out / whole map |
| F | Print-frame mode |
| Esc | Leave frame mode, otherwise Reset |
| ? | Help dialog |

## Replay

- The range is the first to last point of the last Refresh. One clock (`playT` in `tracks-draw.js`, epoch ms,
  `Infinity` = off) drives all ships; x1 is real time, speed 1-10 in steps of 0.2 (remembered), each frame step capped at 250 ms.
- While it is on, only the hulls show: `replayLayer` draws one hull per ship at the clock time (position interpolated
  inside a segment, heading turned the short way, hidden before the first point, after the last and inside a gap
  longer than the gap field); the tracks and the static silhouettes are hidden (owner's decision, 2026-11-12).
  Only `replayLayer` is repainted per frame.
- Play/Pause toggles, Stop rewinds (overlay stays), Reset removes the overlay, the scrubber seeks (also while playing).
  On touch devices the bar adds **-10 s** / **+10 s** buttons (the J / L keys do the same on a keyboard), and the replay
  pauses while the scrubber is held and resumes on release if it was playing; only the finger that grabbed the
  scrubber ends the drag, so a second finger lifting elsewhere does not resume it, and a second finger on the scrubber
  does not take the drag over. Released at the very end, the replay stays there instead of restarting.
- Refresh and Clear reset the replay; Print always renders the static view (replay off during the export).
- A hull too small to see at the current zoom (shorter than twice the marker radius on screen) gets a dot of fixed
  screen size on top, on every pointer, desktop included. Replay only: Print never replays, so it never prints it.
- Measured (headless Chrome, 24 h x 6 ships at 1 Hz): 60 fps at fit zoom up to mid-range and at maximum zoom;
  about 43 fps at fit zoom with 90 % of the points played.

## Known limits

- Silhouettes are drawn at 2 px/m, positions use 2.407 px/m: hulls are about 17 % small (as on `/chart`).
- Logs written before `TRACKS_LOG_ZONE` was set are read with the current setting; a host that changed
  zone mid-history shows those days shifted.
- One source at a time: serial and UDP log alike and a line does not name its source, so one ship fed
  by both would zigzag. No de-duplication.
- History is bounded by `TRACKING_LOG_TOTAL_CAP` as well as `maxHistory`.
- 24 h of six ships is about 500 k points; panning that in software rendering is about 10 fps.
- Time labels overlap when zoomed out.
- Landscape phone: the map is small (the stacked canvas has a 10 rem floor) and map plus bar fill the whole screen
  above the bottom nav, so the message above the map is scrolled off after Refresh.
- Notch / safe area in landscape: [niezweryfikowane].
- Performance of a 24 h range (about 500 k points) on phones is not measured.
- A portrait window of 992 px or more with a mouse keeps the side panel (the rule is `pointer: fine`), while `/chart`
  has no orientation rule.
- System text scaling (large fonts) is not accounted for in the bar's fixed 44 px rows.
- The map raster is 2666 x 4000, so the background softens on a small print frame.

## Supported viewers

Desktop Chrome, Edge, Firefox and Safari (current versions) on Windows, macOS and Linux, and phones and tablets
(iOS Safari, Android Chrome). The server runs on Linux only (Docker or the JAR). Viewers may sit in any time zone:
every time on the page and in the PDF is in the display zone (`tracks.display-zone`, Europe/Warsaw by default).

- **Gestures:** one finger drags, two fingers pinch (`bindStagePinch()` in `static/js/common/stage-pinch.js`, shared
  with `/chart`), and the zoom buttons work as on desktop.
- **Layout rule (S):** the panel sits beside the map when `(min-width: 992px) and (orientation: landscape)` or
  `(min-width: 992px) and (pointer: fine)` matches (`wideLayout` in `tracks-script.js`, literally the same query as
  in `tracks.css`); otherwise it is stacked under the map, which takes the full width, with the bar and the page strip
  below it left free for scrolling.
- **Touch controls:** replay buttons and the scrubber are 44 px high, form fields are 16 px (iOS does not zoom into
  them) and From / To stack because a 16 px date with seconds does not fit half the panel, the Keyboard shortcuts
  button stays visible everywhere.
- **Print** (and the Print frame) is hidden on touch-only devices (`pointer: coarse`); it is offered wherever a fine
  pointer exists.

Print opens its PDF window synchronously in the click (named `tracksPdf`) and the form posts into
it once the image is ready, so Safari and Firefox do not take it for a pop-up. If the browser blocks
it anyway the page says so.

## Rollover guarantee

A query never blocks logback's size rollover and no frame is lost across it.

- **Automated:** `IngestDuringTrackQueryTest#noFrameIsLostWhenTheLogRollsOverUnderTheReader` attaches
  a real `RollingFileAppender` with `SizeAndTimeBasedRollingPolicy` (4KB), holds a `TrackLogSource`
  stream open on `tracking.log`, ingests 201 frames past several rollovers and checks that archives
  appeared, the active file restarted and `TrackQueryService` finds all 201 points.
- **Against the running app, Linux, 2026-11-11:** 5 size rollovers in 10 minutes under a query every
  second; 480 of 480 requests returned 200, no `RolloverFailure` or rename error, 597 points per
  ship over 596 s with a largest gap of 1.004 s.

To repeat it by hand: save as `/tmp/logback-rollover.xml` (the application's
`SAVE-TRANSMISSION-FILE` with `maxFileSize` 200KB):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration info="false">
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>%d{dd-MM-yyyy HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern></encoder>
    </appender>
    <appender name="SAVE-TRANSMISSION-FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/tracking.log</file>
        <append>true</append>
        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <Pattern>%d{dd-MM-yyyy HH:mm:ss.SSS} - %msg%n</Pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/%d{yyyy/MM,aux}/tracking_%d{dd-MM-yyyy}.%i.log.gz</fileNamePattern>
            <maxFileSize>200KB</maxFileSize>
            <maxHistory>365</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
    </appender>
    <root level="info">
        <appender-ref ref="STDOUT"/>
    </root>
    <logger name="com.jaworski.serialprotocol.service.utils.MessageTranslator" additivity="false" level="info">
        <appender-ref ref="SAVE-TRANSMISSION-FILE"/>
    </logger>
</configuration>
```

1. From the project folder: `java -jar target/serial-protocol-3.0.jar --logging.config=/tmp/logback-rollover.xml`
2. Feed it with the simulator: `java -jar simulator/target/ship-sim.jar generate --scenario simulator/scenarios/six-ships.yaml --loop`.
3. Query every second:
   `from=$(date -d '-1 hour' +%Y-%m-%dT%H:%M:%S); to=$(date +%Y-%m-%dT%H:%M:%S); for i in $(seq 200); do curl -k -s -o /dev/null -w '%{http_code}\n' "https://localhost/tracks/api/points?from=$from&to=$to&models=1,2,3,4,5,6"; sleep 1; done`
4. Pass when `.gz` archives appear under `logs/yyyy/MM/`, `logs/tracking.log` restarts after each
   roll, the console shows no `Failed to rename` or `RolloverFailure`, and every response is 200.

## Owner checklist: Safari and Edge

Chrome and Firefox were exercised headless during development. In Safari and in Edge, open `/tracks` and:

1. Draw the last hour of all ships.
2. Zoom with the wheel and the buttons, pan by dragging.
3. Draw, move, resize and remove the print frame.
4. Press Print: the PDF opens in a new tab named `tracks_<day>_<from>-<to>.pdf` (check the filename) with no
   pop-up warning.
5. Check that the times on the page and in the PDF are Europe/Warsaw, also with the computer set to
   another time zone.

## Phones and tablets: verification

Verified in Chromium emulation (headless Chrome 157, synthetic data: two ships, 1801 points; 360x640, 390x844,
844x390, 667x375, 820x1180, 1180x820, 1024x1366, 1366x1024 touch, 1000x1100 and 1440x900 fine pointer):

- Tracks draw after Refresh; the only console message is the unrelated `/favicon.ico` 404.
- No horizontal page overflow; the replay bar is inside the viewport; the panel is beside the map exactly when
  `wideLayout` matches. At 1000x1100 with a mouse the panel shrinks (`flex: 0 1 400px`) instead of pushing the map
  off the left edge.
- Touch: 44 px replay buttons, 16 px fields, Print hidden; on a mouse 14 px fields, Print visible, -10 s / +10 s hidden.
- Pinch out and back in returns to the same scale; scrubbing pauses the replay, moves the scrubber without changing
  the URL and resumes on release.
- Rotation: the whole map stays the whole map; a zoomed view keeps its scale and centre (0 px shift); 844x390 to
  390x844 below the new minimum zoom becomes the whole map (expected clamp).
- After Refresh the whole replay bar sits above the bottom nav (`revealResult()` + `scroll-margin-bottom`), landscape
  phones included (844x390, 667x375: map 160 px, bar ending at the nav's top edge).
- A `pointerup` from another pointer during a scrub keeps the replay paused; the scrubbing finger's release resumes it.
  A second `pointerdown` on the scrubber does not take the drag over; a scrub released at the end stays there, paused.
- From / To show the whole `dd/MM/yyyy HH:mm:ss` on touch at 320-412 px and on landscape tablets (stacked, 16 px);
  side by side at 14 px with a mouse (1280x800, 1000x1100).

[niezweryfikowane] on real devices: iOS `ResizeObserver` on rotation; pointer events on `input[type=range]` in Safari;
`svh` with the collapsing Safari bar; iOS focus zoom with 16 px fields, including Flatpickr; iPad Split View / Slide
Over; the Android keyboard; 44 px targets and the scrub feel.

### Owner checklist: phone and tablet

1. iPhone Safari, portrait: open `/tracks`, draw the last hour. Pass: the map fills the width, the replay bar is fully
   visible above the bottom nav, no sideways scroll.
2. Rotate to landscape and back with the whole map and then zoomed. Pass: no dead strip, no clipped map, the view
   keeps its centre (or the whole map).
3. Drag with one finger and pinch with two on the map, then scroll the page from the strip below it. Pass: no jump
   at the start of a pinch, the page scrolls.
4. Hold and drag the scrubber while playing. Pass: the replay pauses, follows the finger, resumes on release.
5. Tap each date field and the Flatpickr calendar time inputs. Pass: Safari does not zoom the page.
6. iPad: portrait stacked, landscape side-by-side; try Split View. Pass: the layout follows rule S, no overlap.
7. Android Chrome: open the keyboard in a field. Pass: the field stays visible and the layout recovers on close.
8. Check Print and Print frame are not shown, `-10 s` / `+10 s` work, targets are easy to hit.

