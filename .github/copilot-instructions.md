# Copilot instructions for `serial-protocol`

## Build, test, and lint commands
- Full test suite: `.\mvnw.cmd clean test`
- Run one test class: `.\mvnw.cmd -Dtest=MapControllerTest test`
- Run one test method: `.\mvnw.cmd -Dtest=MapControllerTest#test_IndexEndpoint test`
- Build JAR (with tests): `.\mvnw.cmd clean package`
- Build JAR (skip tests, same behavior as `build-jar.bat`): `.\mvnw.cmd clean package -Dmaven.test.skip=true`
- Run locally from Maven: `.\mvnw.cmd spring-boot:run`
- Run packaged app: `java -jar .\target\serial-protocol-1.8.jar`
- Docker stack (app + MariaDB): `docker compose up --build` (or `.\start_docker.sh` in bash environments)

No dedicated lint command is configured in `pom.xml` (no Checkstyle/PMD/SpotBugs plugin execution).

## High-level architecture
- **Boot flow:** `SerialProtocolApplication` starts Spring, then explicitly calls `StartUp.start()` to open serial ports and (optionally) check external REST connectivity.
- **Serial ingest pipeline:** `SerialController` opens ports discovered by jSerialComm and filtered by `rs.comports`; `SerialPortListenerImpl` receives delimited frames (`rs.message_delimiter`), publishes raw payload to `/rs`, translates payload to `ModelTrackDTO`, and publishes JSON to `/json`.
- **Protocol translation:** `MessageTranslator` dispatches by message length (`27` for common models, `29` for Lady Marie). Low-level decoding is in `MessageCommon` and `MessageLadyMarie`, both implementing `SerialMessageTranslator`.
- **Realtime UI channeling:** `WebSocketConfiguration` wires four socket channels (`/rs`, `/json`, `/heartbeat`, `/session`) using `SessionType`. `WSSessionManager` stores sessions globally; `WebSocketPublisherImpl` fans out via the custom thread pool (`ThreadPoolExecutorConfig`).
- **UI surface:** Thymeleaf pages (`templates/`) are rendered by MVC controllers (`MapController`, `CustomDBController`). Front-end scripts in `static/js/` consume WebSocket streams for terminal output, chart rendering, heartbeat time, and active session count.
- **Persistence:** JPA/Hibernate with MariaDB in main profile (`application.properties`), H2 in tests (`src/test/resources/application.properties`). There are two domains: legacy `Student`/`Instructor` and extended training entities under `entity.custom` managed by `CustomDBController` and `service.db.custom`.

## Key codebase conventions
- **WebSocket endpoint contract is shared between Java and JS.** Keep `SessionType` values and JS constants aligned (`/json`, `/rs`, `/heartbeat`, `/session`) when changing endpoints.
- **Serial frame completeness is delimiter-driven.** End-of-message depends on `rs.message_delimiter`; default `13,10` is used both in app and docker config.
- **Tracking page depends on log format, not DB.** `TrackService` reads `logs/<tracking>.log` and parses lines using `LogPatternMatcher` regexes that expect `MessageTranslator` log format (`Translated message: ModelTrackDTO(...)`). Changing log text in translator breaks tracks parsing.
- **Model identity mapping is centralized.** Ship IDs and names are defined in `Models`; serial prefixes (`w1`, `b2`, etc.) are mapped in `MessageTranslator.MODEL_MAP`; JS color mapping mirrors IDs in `static/js/common/enums.js`.
- **Security credentials are property-driven in-memory users.** `SecurityConfig` builds users from `custom.server.credentials.*` properties and protects `/name-service`, `/instructor-service`, and `/api/**`; tests commonly authenticate with `user:user`.
- **Custom domain entities use UUID primary keys plus business IDs.** In `entity.custom`, UUID is DB identity while numeric IDs (e.g., `Courses.id`, `Participant.id`) are managed in services (`nextId()` patterns). Preserve both when adding write paths.
- **Tests assume test-specific Spring properties.** Test runtime uses H2 and `server.port=8081` from `src/test/resources/application.properties`; avoid hardcoding prod DB assumptions in tests.
