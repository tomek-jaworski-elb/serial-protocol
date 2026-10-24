# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This file is the single source of truth for agent guidance.** The other agent files are pointers to it:

- `AGENTS.md` (Codex, Cursor, Copilot, Windsurf, Zed, Jules, Aider) mirrors exactly three blocks of this file, verbatim: **commands**, **invariants** and **conventions**. Each is fenced here by `<!-- mirror:begin NAME -->` / `<!-- mirror:end NAME -->` and by the same markers in `AGENTS.md`. Nothing else is copied.
- `.github/copilot-instructions.md` is a pointer with no content of its own.
- `GEMINI.md` imports this file (`@./CLAUDE.md`) and the four rule files, so Gemini sees everything unconditionally.

When you edit a mirrored block, make the same edit inside the markers in `AGENTS.md` in the same change. Put every other piece of new guidance in the matching path-scoped rule under `.claude/rules/` (see "Where the rest lives"), or here if it must hold in every session.

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
- **Log strings are an API.** `MessageTranslator` logs `"Translated message: ModelTrackDTO(...)"` and `/tracks` replays tracks by parsing it back out of the tracking log; there is no track table. Rewording the log breaks replay. (.claude/rules/ingest-pipeline.md § Track replay)
- **Count open pages with `WebSocketPublisher.openPageCount()`**, never by counting `WSSessionManager`: one page holds one to three connections. (.claude/rules/websocket-sessions.md § WebSocket channels)
- **A page's liveness comes from messages the page sends** (`lastHeardFrom`, `ws.session.silence-limit`), never from server writes. The open/close broadcast on `/session` is required and stays, but any server write also resets the container idle timeout for pages that are already gone, so never add a server-side keep-alive. (.claude/rules/websocket-sessions.md § "Session" means four different things here)
- **`SessionType` is a Java-to-JS contract.** Channel names must stay aligned with `static/js/`.
- **Ship ids live in three places that must agree:** `Models`, `MessageTranslator.MODEL_MAP`, and `ModelsOfShips` in `static/js/chart-script.js`. `/tracks` takes its colours from `Models.colorsById()`; `/chart` still reads the JS copy. (.claude/rules/ingest-pipeline.md § Ship model registry)
- **Hull geometry must never depend on the live zoom.** `ChartShipListTest#theHullIsSizedFromTheChartAndNotFromTheZoom` fails if it does. (.claude/rules/web-chart.md § Chart map)
- **List pages never load an `ImageBlob`.** A photo's bytes live in `image_blob` (`ImageBlob`, same uuid as `Image`, no association); `Image` is metadata only. Field-level laziness is inactive (no enhancement plugin), so only `ImageService` reads the blob table, person mappers take image ids from `ImageLink` projections, the to-one associations to `Image`, `Participant` and `CourseCounter` are LAZY, and `ListPagesLoadNoImageBlobsTest` fails if a list page loads a blob row. (.claude/rules/domain-persistence.md § Images and avatars)
- **Serving an image never writes, and an upload commits with its owner.** Thumbnails are made in `ImageService.saveImage` at upload; `getThumbnail` is read-only and scales a stale row for the response without storing it, `ThumbnailBackfill` rebuilds stale rows once after start-up. Controllers hand `UploadedImage` bytes to the services, which store them in the same transaction as the record, so a failed save leaves no orphan and needs no compensating delete. (.claude/rules/domain-persistence.md § Images and avatars)
- **Business numbers come from `IdAllocator`, never from `MAX(id)+1` at render time.** `participants_id`, `courses_id` and `course_counter_counter` are reserved from a row of `id_sequence` locked inside the saving transaction (`next`), consulted together with the highest value in use; the add forms only show `peek` as a placeholder and submit nothing. A course keeps the number it was created with (`CoursesService.update` ignores the request's `id`). Every table is UUID-keyed; `course_type` was the last `AUTO_INCREMENT` and moved in `V6__course_type_uuid.sql`. (.claude/rules/domain-persistence.md § Custom domain module)
- **Every custom-domain and legacy row is versioned, timestamped and soft-deleted.** Entities (including `Student` and `Instructor`) extend `entity.custom.Auditable` (`@Version version`, `created_at`, `updated_at`, `deleted_at`) and declare `@SQLRestriction("deleted_at is null")` plus an `@SQLDelete` UPDATE, so a delete is an UPDATE and Hibernate hides the row from every JPQL, derived and collection query. Not Hibernate's `@SoftDelete`: it forbids LAZY to-ones towards the entity and tombstones join-table rows, so re-adding a removed photo or trainer collided with its own tombstone. Uniqueness pre-checks and `MAX()` queries are therefore **native** (they must see tombstones: a deleted row's number or code is still taken). Update forms post `version` back and the services call `OptimisticLock.check` before copying the request onto the loaded entity; a null version is applied unchecked. (.claude/rules/domain-persistence.md § Custom domain module)
- **Dates are `dd/MM/yyyy`.** Forms use Flatpickr on `type="text"`, never `type="date"`.
- **Primary-image ordering is lexicographic** (`UUID::toString`), never `UUID.compareTo`, which compares both halves as signed longs. Go through `dto.custom.PrimaryImage`.
- **`CustomDBController`'s `@InitBinder` is an anonymous `PropertyEditorSupport`**, not `StringTrimmerEditor`. It turns blank strings into `null`, which optional-field validation depends on.
- **Tests run on H2 with `server.port=8081`** and auth `user:user`, with `spring.flyway.enabled=false` and the schema generated from the mappings. Nothing activates a `test` profile, so `@Profile("!test")` beans still load in full-context tests. The MariaDB migrations run only in `SchemaMigrationTest` (Testcontainers, skipped without Docker).
- **The schema belongs to Flyway.** `src/main/resources/db/migration` is the only thing that changes a production database; `ddl-auto=validate` refuses to start on drift. A mapping change ships with a `V<n>__*.sql` in the same commit, and `SchemaMigrationTest` turns red when the two disagree. (.claude/rules/domain-persistence.md § Persistence)
- **Every frame enters through `FrameIngress`.** `SerialPortListenerImpl` and `UdpPacketHandlerImpl` are adapters and never publish `/rs` or `/json` themselves; `FrameIngestService` publishes the raw bytes first and a DTO only when its checksum verifies. (.claude/rules/ingest-pipeline.md § Ingest pipeline)
- **`src/test/resources/golden/frames.jsonl` is a contract with `simulator/`.** The application's `GoldenFramesTest` and the simulator's `FrameEncoderTest` both read it; a frame-format change edits the file first and turns both red. Regenerate it only with `GoldenFramesWriter` (`-Dgolden.write=true`). Both adapters log `"Received delimited message: [...]"` and the simulator's replay parses it, so that text is an API too. (.claude/rules/ingest-pipeline.md § Data simulator)
<!-- mirror:end invariants -->

## Working conventions

<!-- mirror:begin conventions -->
- Code, comments, commit messages and documentation in English.
- Never commit or push unless explicitly asked. Make new commits; never `--amend`.
- Do not start an IDE or any desktop application.
- Check current documentation for library and API versions before relying on them.
<!-- mirror:end conventions -->

## Where the rest lives

Architecture notes are path-scoped rules in `.claude/rules/`. Claude Code loads each one automatically when a matching file is read or edited; read it yourself when you need the area's context before touching its files.

| Rule | Covers | Loads for |
|---|---|---|
| `ingest-pipeline.md` | Boot flow, serial/UDP ingest, track replay, ship model registry, UDP server, data simulator | `serial/`, `udp/`, `service/ingest`, `service/tracks`, `service/utils`, `dto/Models.java`, `logback-spring.xml`, `simulator/`, golden/frames fixtures |
| `websocket-sessions.md` | WebSocket channels, the four meanings of "session", page liveness | WebSocket config and handlers, `WSSessionManager`, `WebSocketPublisher*`, `SessionType`, `static/js/ws-*.js`, `templates/fragment.html` |
| `web-chart.md` | MVC controllers, shared layout, chart map, follow mode | `controller/web/`, `resources/`, `templates/`, `static/js`, `static/css` |
| `domain-persistence.md` | MariaDB/H2 profiles, Flyway, legacy and custom domain, images and avatars, REST API, security, PDF reports | `entity/`, `repository/`, `dto/`, `mappers/`, `rest/`, `authorization/`, `service/db`, `service/pdf`, `db/migration`, `templates/custom`, `application*.properties` |

Long-form references stay in `docs/`: [`chart-map-view.md`](docs/chart-map-view.md), [`follow-mode-spec.md`](docs/follow-mode-spec.md), [`custom-domain-analysis.md`](docs/custom-domain-analysis.md), [`pdf-report-plan.md`](docs/pdf-report-plan.md), [`e2e-simulator-plan.md`](docs/e2e-simulator-plan.md), [`Opis RS232.txt`](docs/Opis%20RS232.txt).
