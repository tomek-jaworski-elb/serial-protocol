# AGENTS.md

**Read [`CLAUDE.md`](CLAUDE.md) in the repository root before doing anything else. It is the single source of truth for this project and is far more detailed than this file.**

This file exists because most agents (Codex, Cursor, Copilot, Windsurf, Zed, Jules, Aider) look for `AGENTS.md`. It holds exactly three blocks copied verbatim from `CLAUDE.md`, between the `mirror:begin` / `mirror:end` markers below, and nothing else. Architecture, the WebSocket and session model, follow mode, the custom domain, images, PDF and UDP all live only in `CLAUDE.md`.

Edit `CLAUDE.md` first and copy the block here in the same change. Do not add other content to this file.

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

Each one is explained in the `CLAUDE.md` section it names.

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
