# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This file is the single source of truth for agent guidance.** The other agent files are pointers to it:

- `AGENTS.md` (Codex, Cursor, Copilot, Windsurf, Zed, Jules, Aider) mirrors exactly three blocks of this file, verbatim: **commands**, **invariants** and **conventions**. Each is fenced here by `<!-- mirror:begin NAME -->` / `<!-- mirror:end NAME -->` and by the same markers in `AGENTS.md`. Nothing else is copied.
- `.github/copilot-instructions.md` is a pointer with no content of its own.
- `GEMINI.md` imports this file (`@./CLAUDE.md`).

When you edit a mirrored block, make the same edit inside the markers in `AGENTS.md` in the same change. Put every other piece of new guidance here and nowhere else.

## Stack

Java 25, Spring Boot 4.1.1 (`jakarta.*`), Maven wrapper, MariaDB 11.7 / H2 (tests), Thymeleaf 3 + Bootstrap 5.3, Konva 10 (chart), jSerialComm, OpenPDF + PDFBox, Lombok.

## Build & Test Commands

<!-- mirror:begin commands -->
```
.\mvnw.cmd clean test                                          # full test suite
.\mvnw.cmd -Dtest=MapControllerTest test                       # single test class
.\mvnw.cmd -Dtest=MapControllerTest#test_IndexEndpoint test    # single test method
.\mvnw.cmd clean package                                       # build JAR with tests
.\mvnw.cmd clean package -Dmaven.test.skip=true               # build JAR skip tests
.\mvnw.cmd spring-boot:run                                     # run from Maven
java -jar .\target\serial-protocol-3.0.jar                     # run packaged JAR (3.0 = <version> in pom.xml)
docker compose up --build                                      # app + MariaDB
./start_docker.sh -d    |    ./stop_docker.sh                  # rebuild and start detached / stop (bash)
```

No lint plugin (Checkstyle/PMD/SpotBugs) is configured in `pom.xml` — do not invent a lint command. A local run listens on 443 (`https://localhost/`); Docker maps host 8081 to container 443 (`https://localhost:8081/`).
<!-- mirror:end commands -->

## Invariants that break silently

<!-- mirror:begin invariants -->
- **Log strings are an API.** `MessageTranslator` logs `"Translated message: ModelTrackDTO(...)"` and `/tracks` replays tracks by parsing it back out of the tracking log; there is no track table. Rewording the log breaks replay. (CLAUDE.md § Track replay)
- **Count open pages with `WebSocketPublisher.openPageCount()`**, never by counting `WSSessionManager`: one page holds one to three connections. (CLAUDE.md § WebSocket channels)
- **A page's liveness comes from messages the page sends** (`lastHeardFrom`, `ws.session.silence-limit`), never from server writes. The open/close broadcast on `/session` is required and stays, but any server write also resets the container idle timeout for pages that are already gone, so never add a server-side keep-alive. (CLAUDE.md § "Session" means four different things here)
- **`SessionType` is a Java-to-JS contract.** Channel names must stay aligned with `static/js/`.
- **Ship ids live in three places that must agree:** `Models`, `MessageTranslator.MODEL_MAP`, and `ModelsOfShips` in `static/js/chart-script.js`. `/tracks` takes its colours from `Models.colorsById()`; `/chart` still reads the JS copy. (CLAUDE.md § Ship model registry)
- **Hull geometry must never depend on the live zoom.** `ChartShipListTest#theHullIsSizedFromTheChartAndNotFromTheZoom` fails if it does. (CLAUDE.md § Chart map)
- **Dates are `dd/MM/yyyy`.** Forms use Flatpickr on `type="text"`, never `type="date"`.
- **Primary-image ordering is lexicographic** (`UUID::toString`), never `UUID.compareTo`, which compares both halves as signed longs. Go through `dto.custom.PrimaryImage`.
- **`CustomDBController`'s `@InitBinder` is an anonymous `PropertyEditorSupport`**, not `StringTrimmerEditor`. It turns blank strings into `null`, which optional-field validation depends on.
- **Tests run on H2 with `server.port=8081`** and auth `user:user`. Nothing activates a `test` profile, so `@Profile("!test")` beans such as `SchemaMigrationRunner` still load in full-context tests.
- **Custom-domain records are never removed by user code.** The seven `SoftDeletable` entities carry an auto-enabled Hibernate filter that also applies to `findById`, so a deleted row is invisible everywhere, including guards and bulk JPQL. Delete through `SoftDeleteSupport.softDelete` (mark + flush + detach); only `DeletedRecordsService.purge` removes a row. Code that must see deleted rows goes through `SoftDeleteSupport.withDeleted`, and entities loaded there stay in the session cache — detach them before returning. (CLAUDE.md § Soft delete, trash and optimistic locking)
- **Edit forms carry `version`.** Every update path runs `VersionGuard.check`; a mismatch is `ObjectOptimisticLockingFailureException`, handled once in `WebExceptionHandler`. A controller handler that saves an edited record must rethrow that exception, not swallow it under `catch (RuntimeException)`. Course parents are loaded with `find()` then `lock(OPTIMISTIC)`; a lock mode passed to `find()` reaches the versionless `Image`.
- **The four uuid ids use `@AssignedOrGeneratedUuid`.** A preset uuid is kept, which backup restore relies on; `@GeneratedValue(strategy = UUID)` would refuse it as detached. Backups are schema `1.1` (`BackupRow`: DTO + `deletedAt`/`deletedBy`), still read `1.0`, and never contain `version`.
<!-- mirror:end invariants -->

## Working conventions

<!-- mirror:begin conventions -->
- Code, comments, commit messages and documentation in English.
- Never commit or push unless explicitly asked. Make new commits; never `--amend`.
- Do not start an IDE or any desktop application.
- Check current documentation for library and API versions before relying on them.
<!-- mirror:end conventions -->

## Architecture

### Boot flow
`SerialProtocolApplication` → `StartUp.start()` opens serial ports, optionally checks external REST connectivity.

### Serial ingest pipeline
```
SerialController (jSerialComm discovery, filtered by rs.comports)
  → SerialPortListenerImpl (delimiter-framed, rs.message_delimiter=13,10)
    → WebSocketPublisher → /rs  (raw byte array string)
    → MessageTranslator → ModelTrackDTO → WebSocketPublisher → /json
```

`MessageTranslator` dispatches by message length: `27` (common models via `MessageCommon`) or `29` (Lady Marie via `MessageLadyMarie`). The log line `"Translated message: ModelTrackDTO(...)"` is parsed by `TrackService` — changing this log text breaks track replay. Frame format and port discovery notes: [`docs/Opis RS232.txt`](docs/Opis%20RS232.txt).

### Track replay (`/tracks`)
There is no track table. `logback-spring.xml` writes every translated frame to `logs/${logging.tracking.file.name}.log` (`SAVE-TRANSMISSION-FILE`, rolled daily into `logs/yyyy/MM/`). `ReadLogsFileService` reads those files back, `LogPatternMatcher` holds the regexes, `TrackService` rebuilds tracks. The log format *is* the storage format — treat those log strings as an API. The page receives the ship palette as the `shipColors` model attribute (`Models.colorsById()`) and reads it in `tracks.html`.

### WebSocket channels
| Channel | Content |
|---|---|
| `/rs` | raw serial frame as byte-array string |
| `/json` | `ModelTrackDTO` as JSON |
| `/heartbeat` | heartbeat tick (`ws.heartbeat.interval`) |
| `/session` | open-page count (one connection per loaded page, from the footer) |

`SessionType` enum is the shared contract between Java and JS. `WebSocketConfiguration` wires one handler per channel (`RSWebsocketHandler`, `JsonWebSocketHandler`, `HeartBeatWebSocketHandler`, `SessionCountWebSockerHandler` — the typo in that class name is real). `WSSessionManager` stores connections globally; `WebSocketPublisherImpl` fans out via `ThreadPoolExecutorConfig` thread pool.

Ask for the open-page count with `WebSocketPublisher.openPageCount()`, never by counting the whole registry: a page holds one to three connections depending on what it displays, so counting all of them counted subscriptions and showed two tabs as three.

`SessionCountWebSockerHandler` broadcasts the count to every page when a connection opens or closes. A `@Scheduled` sweep, `closeSilentPages()`, runs every `ws.session.silence-check` (30000 ms) and closes `/session` connections that have sent nothing for longer than `ws.session.silence-limit` (150s — generous because a background tab's timers are throttled to roughly once a minute). The sweep does not broadcast itself; closing a connection triggers `afterConnectionClosed`, which removes it and then broadcasts.

### "Session" means four different things here
Be precise about which one you mean, because conflating them is what broke the footer:

| Term | What it is |
|---|---|
| **channel** | a named stream (`/rs`, `/json`, `/heartbeat`, `/session`) — `SessionType` calls these "session types", which is a misnomer |
| **connection** | one page subscribed to one channel; `WSSessionManager` holds all of them |
| **login session** | a signed-in user's server-side session, `server.servlet.session.timeout` |
| **open page** | one loaded page, from load until its tab closes |

The footer's **"Active sessions"** is a count of **open pages** — the label predates this note and is kept deliberately. It works because the footer is on every page and opens exactly one `/session` connection, so counting that channel counts pages. Counting connections instead counted subscriptions, and a page holds one to three of them depending on what it displays: two tabs on the chart used to report three.

A page also sends a keep-alive on that connection every 30s (`js/ws-session-count.js`). It has to come from the page, and the reason is that there are two different clocks:

- The **container idle timeout** (`WebSocketConfiguration`, `setMaxSessionIdleTimeout`, 3 minutes) is reset by traffic in *either* direction. The server writes to every open page whenever another page opens or closes (`broadcastOpenPageCount()`, which is required), and that write resets this clock for pages that are already gone too.
- **`lastHeardFrom`** is written only when the *page* sends something. `closeSilentPages()` compares it with `ws.session.silence-limit`. Server writes cannot touch it.

A window killed by a crash never sends a close frame, so silence on the second clock is the only thing that gives it away; without it the dead page would stay counted for as long as anybody else used the site and the count would only ever climb. Keep the open/close broadcast, and do not add a server-side keep-alive.

### Web surface
Two MVC controller families under `controller/web`:

- `MapController` — serial/realtime pages: `/` (`index`), `/terminal`, `/chart`, `/tracks`, `/about`, `/greeting`, plus `/login`, `/logout`, `/name-service`, `/instructor-service`.
- `CustomDBController` — all custom-domain CRUD; `ImageController`, `PdfReportController`, `DbUtilsController` (`DatabaseBackupService` backup/restore) and `AdminDeletedController` (`/admin/deleted/**`, the trash) round it out. `MyErrorController` + `WebExceptionHandler` handle errors.

`templates/fragment.html` is the shared layout — `head`, `header`, `footer`, `pagination(...)`, `toastNotifications`, `imageEditor(...)` fragments. Every page pulls in the footer, which is what makes the open-page count work. Menu entries behind `web.ui.extended=true` are gated in the template by comparing the property **as a string** (`@environment.getProperty(...) == 'true'`). `static/bootstrap-5-3-8` is the version actually referenced; `bootstrap-5-3-7` is a leftover.

### Chart map (`/chart`)
Full reference: [`docs/chart-map-view.md`](docs/chart-map-view.md)

A single Konva stage with four layers bottom-to-top: map image → tracks → ship polygons (clickable) → tooltip. Ships live in **map coordinates** (2666 × 4000, `MAP_WIDTH`/`MAP_HEIGHT`); zoom/pan transforms the whole stage, so calibration is resolution-independent. The map raster has three identically-sized variants loaded as a fallback chain (`.avif` ~109 KB → `.webp` ~143 KB → `.jpg` ~1 MB). Konva wipes its container on stage creation, so the zoom buttons must be siblings of `#konvaContainer`, never children.

Hull size is fixed in map coordinates (`modelsConfig[id].scale`) and must never be derived from the live stage scale. The tooltip layer, by contrast, is counter-scaled to keep a constant on-screen size.

#### Follow mode
Full reference: [`docs/follow-mode-spec.md`](docs/follow-mode-spec.md). Toggling a ship in the list makes the camera hold that ship, with the chart turned so its course (COG, default) or heading (HDG) points up.

- **State lives in JS** (`followedShipId` in `chart-script.js`); the DOM only reflects it. Never read the mode back out of the markup.
- **The camera rotates three layers, not the stage**: map, tracks and ships. The tooltip layer is not rotated, so its text stays upright, but its anchor must come from `konvaShipLayer.getAbsoluteTransform()`.
- **Every ship's position is eased** over `chart.ship.position-smoothing-ms` (default 300, `0` restores teleporting) in *both* views, because easing only the followed ship would misplace it against the others. A track's last vertex is the position the hull is *drawn* at, not the last one reported.
- **COG is only trusted above `chart.ship.cog-min-speed-kn`** (default 1.0); below it the camera falls back to HDG.
- While following, `clampStagePosition()` does not apply (the chart is rotated) and the anchor limit replaces it. `maxScale` is computed (`updateMaxScale()`), not a flat 3, and the north-up view inherits that ceiling.
- The two settings reach the browser as the `shipPositionSmoothingMs` and `cogMinSpeedKn` model attributes, read once at page render by `MapController` through `Resources`. They have env overrides `SHIP_POSITION_SMOOTHING_MS` and `COG_MIN_SPEED_KN`, and **changing them needs a restart**.

### Persistence
- **Main profile**: MariaDB (`jdbc:mariadb://${DB_HOST_IP:mariadb}:3306/certificates`), credentials via env vars `DB_USER`/`DB_PASSWORD`. `spring.jpa.open-in-view=false`.
- **Test profile**: H2 in-memory (`src/test/resources/application.properties`), `server.port=8081`, `ddl-auto=create`, `open-in-view=true`. Never hardcode prod DB assumptions in tests. Test auth: `user:user`.
- Schema migration: `SchemaMigrationRunner` runs on `ApplicationReadyEvent` alongside `ddl-auto=update`, issuing idempotent MariaDB `ALTER TABLE … MODIFY COLUMN` statements that relax old NOT NULL columns. It is annotated `@Profile("!test")`, but nothing activates a `test` profile, so it also runs in full-context tests — harmless only because `alter()` swallows every exception (H2 rejects the MariaDB syntax). Never assume a migration has run in tests.
- Two domains coexist: legacy `Student`/`Instructor` (`service.db`, REST) and the custom training domain (`entity.custom`).

### REST API
`RestStudent` under `/api/v1`: `POST /students`, `POST /instructors`, `GET /instructors` (JSON in/out). Secured like the rest of `/api/**`.

### Security
In-memory users built from `custom.server.credentials.*` properties (regular + admin). Protected paths: `/name-service`, `/instructor-service`, `/api/**`, `/admin/**`, `/db-utils/**`, `/pdf/**`, and all custom domain CRUD endpoints (`/trainer-service/**`, `/lecturer-service/**`, `/technician-service/**`, `/participant-service/**`, `/courses-service/**`, `/course-type-service/**`, `/course-counter-service/**`).

HSTS header (`max-age=31536000; includeSubDomains; preload`) is set in `SecurityConfig`. SSL via PKCS12, port 443.

HTTP→HTTPS redirect: configurable via `server.http.redirect.enabled`, `server.http.ports`, `server.port`. `TomcatServerConfiguration` validates ports at startup — throws `IllegalStateException` on collision.

### Custom domain module (`entity.custom`)
Full reference: [`docs/custom-domain-analysis.md`](docs/custom-domain-analysis.md)

Entity hierarchy:
```
SoftDeletable (@MappedSuperclass) — deleted_at, deleted_by, version; base of PersonBase, CourseType, CourseCounter, Courses
PersonBase (@MappedSuperclass) — uuid (@Id), name, surname, notes, nickname, email, phoneNumber, address
  ├── Lecturer   @ManyToMany images + primaryImageUuid
  ├── Trainer    @ManyToMany images + primaryImageUuid
  ├── Technician @ManyToMany images + primaryImageUuid
  └── Participant — id: Long (business key, unique), birthDate, image @OneToOne
CourseType  — id (IDENTITY), code (unique), description
CourseCounter — uuid (@Id), counter (unique), image @OneToOne
Courses     — uuid (@Id), id: Long, participant @ManyToOne, courseType @ManyToOne, date range, trainers/lecturers/technicians @ManyToMany
Image       — id: UUID, data: byte[] @Lob LAZY, contentType
```

CRUD shape is the same for every entity: `GET /<x>-service`, `POST /<x>-service/add`, `POST /<x>-service/update`, `POST /<x>-service/delete/{key}`. The key is `{uuid}` for participant, course-counter and courses and `{id}` for trainer, lecturer, technician and course-type. `POST /courses-service/add-participant` is the quick-add from the participant view: it submits only `participantUuid`, `courseTypeId`, `startDate` and `endDate`, so trainers, lecturers, technicians and counter stay empty. Delete marks the row; the trash is `GET /admin/deleted/{entity}`, `POST …/restore/{key}`, `POST …/purge/{key}` (admin only).

Key conventions:
- UUID is DB identity; numeric `id` on `Courses`/`Participant` is a business key managed via `nextId()` (`COALESCE(MAX(id),0)+1`, backed by `CoursesRepository.findMaxCoursesId()` / `ParticipantRepository.findMaxParticipantId()`).
- Person-like DTOs use `id` for UUID in `LecturerDTO`/`TrainerDTO`/`TechnicianDTO`; `ParticipantDTO` uses `participantUuid` (avoids collision with `Long id`).
- All `@AttributeOverride` for UUID includes `nullable=false, updatable=false, unique=true`.
- Date format is `dd/MM/yyyy` (EU). HTML forms use Flatpickr on `type="text"` — never `type="date"`.
- `@InitBinder` in `CustomDBController` registers an anonymous `PropertyEditorSupport` for `String.class` only, turning blank input into `null` — required for optional field validation. (It is *not* `StringTrimmerEditor`; that class appears nowhere in the code. Non-`String` types such as `UUID` go through Spring's default converters.)
- `CoursesMapper.mapToEntity()` is deprecated; use `CoursesService.buildCourses()`, whose `activeParent()` loads each parent through the filter and locks it optimistically.
- `CoursesDTO` has dual counter fields: `courseCounterUuid` (read-only, set by mapper) and `counter` (Long, from forms). `resolveCourseCounter()` prefers UUID.
- Deleting a participant, type, counter, trainer, lecturer or technician is guarded: `IllegalStateException` while an *active* course uses it. Deleted courses block nothing.
- `CourseType.code` is unique at DB level and checked in `CourseTypeService` through `existsByCode()` / `existsByCodeAndIdNot()`.
- `CourseCounterDTO` is a Java `record` (Spring 6.1+ constructor binding required); like every custom DTO it carries `version`.

### Images and avatars
- Uploads are capped at 10 MB (`spring.servlet.multipart.max-file-size` and `max-request-size`). Single-image entities (participant, course-counter) go through `ImageUploadCoordinator.uploadSingleImage()`; multi-image people through `uploadImages()`, limited to `MAX_UPLOAD_IMAGES`.
- One endpoint serves everything: `GET /custom/image/{uuid}`, optionally `?size=thumb` — the *only* accepted value, anything else is a 400 so a single URL cannot spawn unbounded resize work. `ThumbnailGenerator.MAX_EDGE=160`.
- ETags derive from the uuid alone (`"{uuid}-orig"` / `"{uuid}-thumb-{ThumbnailGenerator.VERSION}"`) because image bytes never change — replacing a photo creates a new `Image` row. Bump `ThumbnailGenerator.VERSION` when thumbnail rendering changes; that invalidates thumbs only, not originals. A 304 still verifies existence, otherwise deleted photos would stay cached forever.
- Deleting an owner keeps its photos (a restore needs them); `DeletedRecordsService.purge` removes the ones nothing else uses.
- `Cache-Control: private, no-cache` is set explicitly and survives Spring Security's `no-store` writer, which skips whenever the header is already present.
- Multi-image people (`Lecturer`/`Trainer`/`Technician`) carry a `primaryImageUuid` pointer; `dto.custom.PrimaryImage` is the single source of truth for resolving it, shared by services (on write) and DTOs (on render). Sort candidate UUIDs **lexicographically by `toString()`**, never `UUID.compareTo` (it compares both halves as signed longs). An absent pointer in a request means "leave it alone", and fallback prefers pre-existing photos so a newly uploaded one never silently becomes the avatar.

### Soft delete, trash and optimistic locking
Full reference: [`docs/soft-delete.md`](docs/soft-delete.md)

A user's delete marks the row (`deleted_at`, `deleted_by`); the `activeOnly` filter on `SoftDeletable` is auto-enabled and applies to lookups by id, so every list, `findById`, guard and bulk JPQL sees active rows only. `SoftDeleteSupport` holds the few operations around it: `currentUser()` (`"system"` when nobody is signed in), `softDelete()` (mark, flush, detach — the session cache would otherwise hand the row back), and `withDeleted()` / `runWithDeleted()` (filter off for the rest of the transaction, re-entrant). Guards count active courses only; `nextId()` and the unique-value checks run with the filter off so deleted rows keep their ids, codes and counters. A course's parents go through `CoursesService.activeParent` (`find()`, then `lock(OPTIMISTIC)`).

The trash is `/admin/deleted/{entity}` (`AdminDeletedController`, `DeletedRecordsService`, `DeletedKind`): list, restore (refused while a parent of a course is in the trash), purge (refused while any course, deleted or not, points at the record; JPQL bulk delete, then photos nothing else uses). The service detaches every hidden row it loaded before returning.

`version` lives on `SoftDeletable`; DTOs and the seven edit forms carry it (`data-version` → hidden field), `VersionGuard` checks it in every update path, and `WebExceptionHandler` turns `ObjectOptimisticLockingFailureException` into one flash message plus a redirect to the `Referer`. Update paths `saveAndFlush`. Backups (schema `1.1`, `BackupRow<T>`) carry deleted rows and read `1.0` files as active rows; `@AssignedOrGeneratedUuid` is what lets a restore insert rows under their old uuids.

### Ship model registry
Defined in the `Models` enum (id, display name, colour) and, for serial prefixes, in `MessageTranslator.MODEL_MAP`:
| Serial prefix | Model | ID |
|---|---|---|
| `w1` | WARTA | 1 |
| `b2` | BLUE_LADY | 2 |
| `d3` | DORCHERTER_LADY | 3 |
| `c4` | CHERRY_LADY | 4 |
| `k5` | KOLOBRZEG | 5 |
| `l6` | LADY_MARIE | 6 |

The enum declares `CHERRY_LADY` before `DORCHERTER_LADY`; order is irrelevant, lookup is by id. `Models.colorsById()` is the palette source for `/tracks`. `/chart` still reads its own copy, `ModelsOfShips` in `static/js/chart-script.js` (the key for ship 2 is spelled `BLEUE_LADY` there) — keep it in sync with the enum.

### PDF reports
`PdfReportController` (`/pdf/**`, one POST per entity type) → `PdfReportService` with `PdfPageLayout`, `PdfHeaderFooterEvent`, `PdfColorScheme`. Configuration prefix: `pdf.report.*` (`PdfReportProperties`). Max records: `pdf.report.max-records=100`. Fonts are bundled in `src/main/resources/fonts` (DejaVuSans — required for Polish glyphs). Design notes and library comparison: [`docs/pdf-report-plan.md`](docs/pdf-report-plan.md).

### UDP server
Optional UDP listener: `udp.server.enabled=${UDP_SERVER_ENABLED:true}`, port `udp.server.port=${UDP_SERVER_PORT:2222}`, buffer `udp.server.buffer-size`. `UdpServer` → `UdpDispatcher` → `UdpPacketHandlerImpl`; properties bound by `UdpProperties`.
