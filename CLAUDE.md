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
.\mvnw.cmd -f simulator/pom.xml verify                        # build + test the data simulator (simulator/target/ship-sim.jar)
run-sim.bat replay --from docs\LogTerminal.txt --loop          # feed the running app over UDP (localhost:2222) from the recording
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
- **List pages never load an `ImageBlob`.** A photo's bytes live in `image_blob` (`ImageBlob`, same uuid as `Image`, no association); `Image` is metadata only. Field-level laziness is inactive (no enhancement plugin), so only `ImageService` reads the blob table, person mappers take image ids from `ImageLink` projections, the to-one associations to `Image`, `Participant` and `CourseCounter` are LAZY, and `ListPagesLoadNoImageBlobsTest` fails if a list page loads a blob row. (CLAUDE.md § Images and avatars)
- **Serving an image never writes, and an upload commits with its owner.** Thumbnails are made in `ImageService.saveImage` at upload; `getThumbnail` is read-only and scales a stale row for the response without storing it, `ThumbnailBackfill` rebuilds stale rows once after start-up. Controllers hand `UploadedImage` bytes to the services, which store them in the same transaction as the record, so a failed save leaves no orphan and needs no compensating delete. (CLAUDE.md § Images and avatars)
- **Business numbers come from `IdAllocator`, never from `MAX(id)+1` at render time.** `participants_id`, `courses_id` and `course_counter_counter` are reserved from a row of `id_sequence` locked inside the saving transaction (`next`), consulted together with the highest value in use; the add forms only show `peek` as a placeholder and submit nothing. A course keeps the number it was created with (`CoursesService.update` ignores the request's `id`). Every table is UUID-keyed; `course_type` was the last `AUTO_INCREMENT` and moved in `V6__course_type_uuid.sql`. (CLAUDE.md § Custom domain module)
- **Every custom-domain and legacy row is versioned, timestamped and soft-deleted.** Entities (including `Student` and `Instructor`) extend `entity.custom.Auditable` (`@Version version`, `created_at`, `updated_at`, `deleted_at`) and declare `@SQLRestriction("deleted_at is null")` plus an `@SQLDelete` UPDATE, so a delete is an UPDATE and Hibernate hides the row from every JPQL, derived and collection query. Not Hibernate's `@SoftDelete`: it forbids LAZY to-ones towards the entity and tombstones join-table rows, so re-adding a removed photo or trainer collided with its own tombstone. Uniqueness pre-checks and `MAX()` queries are therefore **native** (they must see tombstones: a deleted row's number or code is still taken). Update forms post `version` back and the services call `OptimisticLock.check` before copying the request onto the loaded entity; a null version is applied unchecked. (CLAUDE.md § Custom domain module)
- **Dates are `dd/MM/yyyy`.** Forms use Flatpickr on `type="text"`, never `type="date"`.
- **Primary-image ordering is lexicographic** (`UUID::toString`), never `UUID.compareTo`, which compares both halves as signed longs. Go through `dto.custom.PrimaryImage`.
- **`CustomDBController`'s `@InitBinder` is an anonymous `PropertyEditorSupport`**, not `StringTrimmerEditor`. It turns blank strings into `null`, which optional-field validation depends on.
- **Tests run on H2 with `server.port=8081`** and auth `user:user`, with `spring.flyway.enabled=false` and the schema generated from the mappings. Nothing activates a `test` profile, so `@Profile("!test")` beans still load in full-context tests. The MariaDB migrations run only in `SchemaMigrationTest` (Testcontainers, skipped without Docker).
- **The schema belongs to Flyway.** `src/main/resources/db/migration` is the only thing that changes a production database; `ddl-auto=validate` refuses to start on drift. A mapping change ships with a `V<n>__*.sql` in the same commit, and `SchemaMigrationTest` turns red when the two disagree. (CLAUDE.md § Persistence)
- **Every frame enters through `FrameIngress`.** `SerialPortListenerImpl` and `UdpPacketHandlerImpl` are adapters and never publish `/rs` or `/json` themselves; `FrameIngestService` publishes the raw bytes first and a DTO only when its checksum verifies. (CLAUDE.md § Ingest pipeline)
- **`src/test/resources/golden/frames.jsonl` is a contract with `simulator/`.** The application's `GoldenFramesTest` and the simulator's `FrameEncoderTest` both read it; a frame-format change edits the file first and turns both red. Regenerate it only with `GoldenFramesWriter` (`-Dgolden.write=true`). Both adapters log `"Received delimited message: [...]"` and the simulator's replay parses it, so that text is an API too. (CLAUDE.md § Data simulator)
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

### Ingest pipeline (serial and UDP)
```
SerialController (jSerialComm discovery, filtered by rs.comports)
  -> SerialPortListenerImpl (delimiter-framed, rs.message_delimiter=13,10) --+
UdpServer -> UdpDispatcher -> UdpPacketHandlerImpl (one datagram = one frame) --+
                                                                                |
                                                                                v
                              FrameIngress / FrameIngestService - the only pipeline
                                -> WebSocketPublisher -> /rs  (raw byte array string, every frame)
                                -> MessageTranslator -> ModelTrackDTO -> isCRCValid gate -> WebSocketPublisher -> /json
```

The two transports are adapters: they log what arrived and call `FrameIngress.ingest(frame, origin)`, nothing else. Tests and the simulator (`docs/e2e-simulator-plan.md`) inject frames at `FrameIngress`, above the port. `MessageTranslator` dispatches by message length: `27` (common models via `MessageCommon`) or `29` (Lady Marie via `MessageLadyMarie`). The log line `"Translated message: ModelTrackDTO(...)"` is parsed by `TrackService` — changing this log text breaks track replay. Frame format and port discovery notes: [`docs/Opis RS232.txt`](docs/Opis%20RS232.txt).

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
- `CustomDBController` — all custom-domain CRUD; `ImageController` and `PdfReportController` round it out. `MyErrorController` + `WebExceptionHandler` handle errors.

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
- **Test profile**: H2 in-memory (`src/test/resources/application.properties`), `server.port=8081`, `ddl-auto=create`, `spring.flyway.enabled=false`, `open-in-view=false` (same as production, so the suite can catch `LazyInitializationException`). Never hardcode prod DB assumptions in tests. Test auth: `user:user`.
- **Schema migration is Flyway** (`flyway-core` + `flyway-mysql`, scripts in `src/main/resources/db/migration`). `V1__baseline.sql` is the schema as Hibernate generates it for MariaDB 11.8 from the mappings; `V2__relax_not_null_columns.sql` carries the `ALTER TABLE … MODIFY COLUMN` statements that `SchemaMigrationRunner` used to issue on every start. Production runs `ddl-auto=validate` with `spring.flyway.baseline-on-migrate=true` and `baseline-version=1`: an installation built by the old `ddl-auto=update` is marked as version 1 without executing V1 and then receives V2 and later; a fresh database runs everything. The H2 test profile keeps `ddl-auto=create` with Flyway disabled; `SchemaMigrationTest` (`@Testcontainers`, `mariadb:11.8`, skipped without Docker) applies every script to an empty MariaDB and lets Hibernate validate the result. Every mapping change needs a new `V<n>__*.sql`; never edit an applied script. Before the first deployment with `validate`, start the app once against a copy of the production database: the baseline describes the mapped schema, and an installation grown by `ddl-auto=update` may differ in details (old columns, type widths) that need a corrective migration.
- Two domains coexist: legacy `Student`/`Instructor` (`service.db`, REST) and the custom training domain (`entity.custom`). The legacy one stays by decision: its tables carry the same audit columns and tombstones (`V9__legacy_audit_columns.sql`), its services are transactional, and its `int` keys are assigned by the sending system (unique by origin). `StudentRepository.findLatest` is JPQL so tombstones stay hidden.

### REST API
`RestStudent` under `/api/v1`: `POST /students`, `POST /instructors`, `GET /instructors` (JSON in/out). Secured by the `/api/**` URL rule only; the malformed class-level `@PreAuthorize` is gone (method security was never enabled). Each POST stores its batch atomically through `StudentService.saveNew` (skips known ids) / `InstructorService.saveNew` (skips known surname+name) and returns the stored ids; a failing row now fails the whole batch instead of being logged and skipped.

### Security
In-memory users built from `custom.server.credentials.*` properties (regular + admin). Protected paths: `/name-service`, `/instructor-service`, `/api/**`, `/admin/**`, `/pdf/**`, and all custom domain CRUD endpoints (`/trainer-service/**`, `/lecturer-service/**`, `/technician-service/**`, `/participant-service/**`, `/courses-service/**`, `/course-type-service/**`, `/course-counter-service/**`).

HSTS header (`max-age=31536000; includeSubDomains; preload`) is set in `SecurityConfig`. SSL via PKCS12, port 443.

HTTP→HTTPS redirect: configurable via `server.http.redirect.enabled`, `server.http.ports`, `server.port`. `TomcatServerConfiguration` validates ports at startup — throws `IllegalStateException` on collision.

### Custom domain module (`entity.custom`)
Full reference: [`docs/custom-domain-analysis.md`](docs/custom-domain-analysis.md)

Entity hierarchy:
```
Auditable (@MappedSuperclass) — version (@Version), createdAt, updatedAt, deletedAt; every entity below extends it and carries @SQLRestriction("deleted_at is null") + an @SQLDelete UPDATE (never Hibernate's @SoftDelete)
PersonBase (@MappedSuperclass, extends Auditable) — uuid (@Id), name, surname, notes, nickname, email, phoneNumber, address
  ├── Lecturer   @ManyToMany images + primaryImageUuid
  ├── Trainer    @ManyToMany images + primaryImageUuid
  ├── Technician @ManyToMany images + primaryImageUuid
  └── Participant — id: Long (business key, unique), birthDate, image @OneToOne
CourseType  — id: UUID (@Id, column course_type_uuid), code (unique), description
CourseCounter — uuid (@Id), counter (unique), image @OneToOne
Courses     — uuid (@Id), id: Long, participant @ManyToOne, courseType @ManyToOne, date range, trainers/lecturers/technicians @ManyToMany
Image       — id: UUID, contentType, thumb metadata (no bytes)
ImageBlob   — id = Image id (assigned), data + thumbData @Lob; not Auditable, never loaded by a list
```

CRUD shape is the same for every entity: `GET /<x>-service`, `POST /<x>-service/add`, `POST /<x>-service/update`, `POST /<x>-service/delete/{key}`. The key is `{uuid}` for participant, course-counter and courses and `{id}` for trainer, lecturer, technician and course-type. `POST /courses-service/add-participant` is the quick-add from the participant view: it submits only `participantUuid`, `courseTypeId`, `startDate` and `endDate`, so trainers, lecturers, technicians and counter stay empty.

Key conventions:
- UUID is DB identity everywhere; numeric `id` on `Courses`/`Participant` and `counter` on `CourseCounter` are human-visible business numbers. A `null` number on save is reserved by `IdAllocator.next(sequence, highestInUse)` from the `id_sequence` row of that sequence (`SELECT … FOR UPDATE`, seeded by `V5__id_sequences.sql`, created on first use where the migration has not run), stepping over any higher number typed by hand. `nextId()` / `nextCounter()` are `IdAllocator.peek` and feed only the forms' placeholders. `courses_id` is unique (`V4__unique_courses_id.sql` renumbered duplicates first) and is never changed by an update.
- Person-like DTOs use `id` for UUID in `LecturerDTO`/`TrainerDTO`/`TechnicianDTO`; `ParticipantDTO` uses `participantUuid` (avoids collision with `Long id`).
- All `@AttributeOverride` for UUID includes `nullable=false, updatable=false, unique=true`.
- Date format is `dd/MM/yyyy` (EU). HTML forms use Flatpickr on `type="text"` — never `type="date"`.
- `@InitBinder` in `CustomDBController` registers an anonymous `PropertyEditorSupport` for `String.class` only, turning blank input into `null` — required for optional field validation. (It is *not* `StringTrimmerEditor`; that class appears nowhere in the code. Non-`String` types such as `UUID` go through Spring's default converters.)
- **Validation runs on the DTOs, before any service is called**: every `CustomDBController` POST handler binds `@Valid @ModelAttribute` plus `BindingResult` and, on errors, redirects with a flash `errorMessage` of `field message` pairs (`validationMessage(...)`). The constraints on `dto.custom` mirror the entity columns (`@NotBlank`/`@Size` on person fields, `@Email`, `@Past birthDate`, `@Positive` numbers, `@NotNull` course references and dates, `CoursesDTO.isDateRange()` as `@AssertTrue`). The flush-time `ConstraintViolationException` handling stays as a safety net only. `CustomDBControllerValidationTest` pins the messages.
- `CoursesMapper.mapToEntity()` is deprecated; use `CoursesService.buildCourses()` with `repository.getReferenceById()`.
- `CoursesDTO` has dual counter fields: `courseCounterUuid` (read-only, set by mapper) and `counter` (Long, from forms). `resolveCourseCounter()` prefers UUID.
- Participant deletion is guarded: throws `IllegalStateException` if linked courses exist.
- **Deletes are tombstones** (`V7__audit_columns.sql`): `deleteById` becomes `UPDATE … SET deleted_at`, rows never leave the table, and `count()`/`existsById`/`findById` no longer see them. Soft-deleted images keep their blobs; a purge is a separate, later decision. **Join-table rows are hard-deleted**: soft-deleting a course removes its `courses_trainers`/`courses_lecturers`/`courses_technicians` rows, and dropping a photo from a person removes the `*_image` row. Chosen deliberately (Hibernate's `@SoftDelete` tombstoned them and then collided on re-add); a future merge therefore treats a course's crew and a person's photo set as part of the owning row, versioned by it, not as rows of their own. Updates are load-and-copy everywhere (`ParticipantService.updateByUuid`, `CoursesService.applyTo`, `CourseTypeService.update`), never a merge of a detached DTO-built entity, so the version check and a "not found" for a deleted record both work. Every DTO carries `version`; the templates post it from `data-version` into a hidden input, and the controllers turn `OptimisticLockingFailureException` into a toast.
- `CourseType.code` is unique at DB level and pre-checked in `CourseTypeService` through the native, case-insensitive `countWithCode()` / `countWithCodeExcludingId()` (native so a tombstoned type's code still counts as taken).
- `CourseCounterDTO` is a Java `record` (Spring 6.1+ constructor binding required).

### Images and avatars
- Uploads are capped at 10 MB (`spring.servlet.multipart.max-file-size` and `max-request-size`). The controllers only read the multipart parts (`MultipartImages` → `UploadedImage`); every service has an upload-aware overload (`save(dto, uploads)`, `update(dto, uploads, removals)` for people, `save(dto, upload)` / `update(dto, upload, removeImage)` for participant and counter) that stores the bytes and the record in one transaction. People are limited to `ImageService.MAX_IMAGES_PER_PERSON` (6) photos, checked by `ImageService.assertWithinLimit`, whose `ImageLimitExceededException` is the only message the toasts show verbatim. The request carries the images to *remove*, never the ones to keep (a broken editor then removes nothing). `V3__delete_orphan_images.sql` removed the orphans the old upload-then-save order had left behind.
- One endpoint serves everything: `GET /custom/image/{uuid}`, optionally `?size=thumb` — the *only* accepted value, anything else is a 400 so a single URL cannot spawn unbounded resize work. `ThumbnailGenerator.MAX_EDGE=160`.
- ETags derive from the uuid alone (`"{uuid}-orig"` / `"{uuid}-thumb-{ThumbnailGenerator.VERSION}"`) because image bytes never change — replacing a photo creates a new `Image` row. Bump `ThumbnailGenerator.VERSION` when thumbnail rendering changes; that invalidates thumbs only, not originals. A 304 still verifies existence, otherwise deleted photos would stay cached forever. A bump does not touch the thumbnails on GET: `ThumbnailBackfill` rebuilds every stale row once after start-up (`images.thumbnail.backfill-on-start`, default `true`), and until it has run a stale row is scaled per request.
- `Cache-Control: private, no-cache` is set explicitly and survives Spring Security's `no-store` writer, which skips whenever the header is already present.
- **Reading never touches blobs.** The bytes of a photo are a separate row, `ImageBlob` (`image_blob`, `V8__image_blob_table.sql`), keyed by the `Image` uuid with no JPA association between them; `Image` carries only content type and thumbnail metadata. `ImageService.getOriginal` / `getThumbnail` / `refreshThumbnail` are the only readers of `ImageBlobRepository`, so `resolveImages`, `deleteRemovedImages` and every list are metadata-only. `Trainer`/`Lecturer`/`Technician` services fetch image ids for a whole page with one `findImageLinks(...)` query (`repository.custom.ImageLink`) and the mappers take those ids as a parameter instead of calling `getImages()`; `Participant.image`, `CourseCounter.image` and the to-one associations of `Courses` are LAZY and the mappers only read ids off the proxies. `hibernate.default_batch_fetch_size=50` turns the remaining lazy loads into one IN query per page. `CoursesService.findByParticipantUuids` / `findByCourseCounterUuids` replace the old one-query-per-row loops. `ListPagesLoadNoImageBlobsTest` asserts zero `ImageBlob` loads on every list page and a row-independent statement count.
- Multi-image people (`Lecturer`/`Trainer`/`Technician`) carry a `primaryImageUuid` pointer; `dto.custom.PrimaryImage` is the single source of truth for resolving it, shared by services (on write) and DTOs (on render). Sort candidate UUIDs **lexicographically by `toString()`**, never `UUID.compareTo` (it compares both halves as signed longs). An absent pointer in a request means "leave it alone", and fallback prefers pre-existing photos so a newly uploaded one never silently becomes the avatar.

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
Optional UDP listener: `udp.server.enabled=${UDP_SERVER_ENABLED:true}`, port `udp.server.port=${UDP_SERVER_PORT:2222}`, buffer `udp.server.buffer-size`. When disabled, `UdpServer`, `UdpDispatcher` and `UdpPacketHandlerImpl` are not created at all (`@ConditionalOnBooleanProperty`). `UdpServer` → `UdpDispatcher` → `UdpPacketHandlerImpl` → `FrameIngress`; properties bound by `UdpProperties`. A datagram is treated exactly like a serial frame — logged as `"From UDP <sender> Received delimited message: [...]"` (the phrase the simulator's replay parses, so a UDP-fed session can be replayed from `log.log`), published raw on `/rs`, translated, dropped unless the checksum verifies, then published on `/json` — so UDP is the transport for simulated traffic. It used to skip `/rs` and the checksum gate; that is why the pipeline was pulled out of the adapters. The port has no authentication: stray datagrams are logged at WARN (no stack trace) and, like serial noise, shown raw on `/terminal`; the lab compose does not publish the port.

### Data simulator (`simulator/`)
`simulator/` is a separate Maven build (own `pom.xml`, no Spring, Java 25, picocli 4.7.7, Jackson 3), **not** a module of this pom: the application never depends on it. It feeds frames to a running application over UDP — the default sink is `udp://localhost:2222` — so they enter through `FrameIngress` like a serial frame. Build with `.\mvnw.cmd -f simulator/pom.xml verify`; `run-sim.bat` builds the JAR when missing and runs it with the JDK from `JAVA_HOME` (the JAR is compiled for Java 25, a Java 21 on `PATH` refuses it). Commands: `replay` (recorded `Received delimited message: [...]` lines; `docs/LogTerminal.txt` holds 61 frames of all six ships), `generate` (a YAML scenario in `simulator/scenarios/`: ships moved by a Nomoto first-order yaw model with `K`, `T`, `accel-time` per ship from `scenarios/ships.yaml`, scripted `engine` uncoupled from speed by decision, six fault kinds) and `raw`. `generate --to jsonl://...` wrote the application's fixtures in `src/test/resources/frames/` (`six-ships.jsonl`, `faults.jsonl`), which `ScenarioFixtureTest` replays through `MessageTranslator`; regenerate them from the same scenarios when the scenarios change. The codec contract with this application is the golden file (invariants above). Plan, decisions and what was dropped: [`docs/e2e-simulator-plan.md`](docs/e2e-simulator-plan.md); usage: [`simulator/README.md`](simulator/README.md). When watching `/chart`, keep its tab in the foreground — `chart-script.js` closes the `/json` socket of a hidden tab and reopens it when the tab is shown — and keep `app.runs.test-enabled` at `false`: that older flag only animates hulls in the browser (`runShipTest` in `chart-script.js`, no frames, nothing on `/terminal` or `/tracks`) and would fight the simulator for the same hulls.
