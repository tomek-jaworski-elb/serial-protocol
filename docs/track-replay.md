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
| `static/js/common/ship-geometry.js`, `map-image.js` | Hull geometry and map loading, shared with `/chart`. |

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
- While it is on: the full tracks are faded (layer opacity), `replayLayer` repeats the played part in full colour (no
  dots, which would not fit a frame at low zoom) and draws one hull per ship at the clock time: position interpolated
  inside a segment, heading turned the short way, hidden before the first point, after the last and inside a gap
  longer than the gap field. Static silhouettes are hidden. Only `replayLayer` is repainted per frame.
- Play/Pause toggles, Stop rewinds (overlay stays), Reset removes the overlay, the scrubber seeks (also while playing).
  Refresh and Clear reset the replay; Print always renders the static view (replay off during the export).
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
- The map raster is 2666 x 4000, so the background softens on a small print frame.

## Supported viewers

Desktop Chrome, Edge, Firefox and Safari (current versions) on Windows, macOS and Linux. The server
runs on Linux only (Docker or the JAR). Viewers may sit in any time zone: every time on the page
and in the PDF is in the display zone (`tracks.display-zone`, Europe/Warsaw by default). Tablets
and phones are not supported.

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
