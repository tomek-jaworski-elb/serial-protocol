# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```
.\mvnw.cmd clean test                                          # full test suite
.\mvnw.cmd -Dtest=MapControllerTest test                       # single test class
.\mvnw.cmd -Dtest=MapControllerTest#test_IndexEndpoint test    # single test method
.\mvnw.cmd clean package                                       # build JAR with tests
.\mvnw.cmd clean package -Dmaven.test.skip=true               # build JAR skip tests
.\mvnw.cmd spring-boot:run                                     # run from Maven
java -jar .\target\serial-protocol-2.0.jar                     # run packaged JAR
docker compose up --build                                      # app + MariaDB
```

No lint plugin (Checkstyle/PMD/SpotBugs) is configured in `pom.xml`.

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

`MessageTranslator` dispatches by message length: `27` (common models via `MessageCommon`) or `29` (Lady Marie via `MessageLadyMarie`). The log line `"Translated message: ModelTrackDTO(...)"` is parsed by `TrackService` — changing this log text breaks track replay.

### WebSocket channels
| Channel | Content |
|---|---|
| `/rs` | raw serial frame as byte-array string |
| `/json` | `ModelTrackDTO` as JSON |
| `/heartbeat` | heartbeat tick |
| `/session` | active session count |

`SessionType` enum is the shared contract between Java and JS. `WSSessionManager` stores sessions globally; `WebSocketPublisherImpl` fans out via `ThreadPoolExecutorConfig` thread pool.

### Persistence
- **Main profile**: MariaDB (`jdbc:mariadb://${DB_HOST_IP:mariadb}:3306/certificates`), credentials via env vars `DB_USER`/`DB_PASSWORD`.
- **Test profile**: H2 in-memory (`src/test/resources/application.properties`), `server.port=8081`. Never hardcode prod DB assumptions in tests. Test auth: `user:user`.
- Schema migration: `SchemaMigrationRunner` runs alongside `ddl-auto=update`.

### Security
In-memory users built from `custom.server.credentials.*` properties. Protected paths: `/name-service`, `/instructor-service`, `/api/**`, `/admin/**`, `/db-utils/**`, `/pdf/**`, and all custom domain CRUD endpoints (`/trainer-service/**`, `/lecturer-service/**`, `/technician-service/**`, `/participant-service/**`, `/courses-service/**`, `/course-type-service/**`, `/course-counter-service/**`).

HSTS header (`max-age=31536000; includeSubDomains; preload`) is set in `SecurityConfig`. SSL via PKCS12, port 443.

HTTP→HTTPS redirect: configurable via `server.http.redirect.enabled`, `server.http.ports`, `server.port`. `TomcatServerConfiguration` validates ports at startup — throws `IllegalStateException` on collision.

### Custom domain module (`entity.custom`)
Full reference: [`docs/custom-domain-analysis.md`](docs/custom-domain-analysis.md)

Entity hierarchy:
```
PersonBase (@MappedSuperclass) — uuid (@Id), name, surname, notes, email, phoneNumber, address
  ├── Lecturer   @ManyToMany images
  ├── Trainer    @ManyToMany images
  ├── Technician @ManyToMany images
  └── Participant — id: Long (business key, unique), birthDate, image @OneToOne
CourseType  — id (IDENTITY), code (unique), description
CourseCounter — uuid (@Id), counter (unique), image @OneToOne
Courses     — uuid (@Id), id: Long, participant @ManyToOne, courseType @ManyToOne, date range, trainers/lecturers/technicians @ManyToMany
Image       — id: UUID, data: byte[] @Lob LAZY, contentType
```

Key conventions:
- UUID is DB identity; numeric `id` on `Courses`/`Participant` is a business key managed via `nextId()` (`COALESCE(MAX(id),0)+1`).
- Person-like DTOs use `id` for UUID in `LecturerDTO`/`TrainerDTO`/`TechnicianDTO`; `ParticipantDTO` uses `participantUuid` (avoids collision with `Long id`).
- All `@AttributeOverride` for UUID includes `nullable=false, updatable=false, unique=true`.
- Date format is `dd/MM/yyyy` (EU). HTML forms use Flatpickr on `type="text"` — never `type="date"`.
- `@InitBinder` in `CustomDBController` registers `StringTrimmerEditor(true)` — required for optional field validation.
- `CoursesMapper.mapToEntity()` is deprecated; use `CoursesService.buildCourses()` with `repository.getReferenceById()`.
- `CoursesDTO` has dual counter fields: `courseCounterUuid` (read-only, set by mapper) and `counter` (Long, from forms). `resolveCourseCounter()` prefers UUID.
- Participant deletion is guarded: throws `IllegalStateException` if linked courses exist.
- `CourseCounterDTO` is a Java `record` (Spring 6.1+ constructor binding required).

### Ship model registry
Defined in `Models` enum and `MessageTranslator.MODEL_MAP`:
| Serial prefix | Model | ID |
|---|---|---|
| `w1` | WARTA | 1 |
| `b2` | BLUE_LADY | 2 |
| `d3` | DORCHERTER_LADY | 3 |
| `c4` | CHERRY_LADY | 4 |
| `k5` | KOLOBRZEG | 5 |
| `l6` | LADY_MARIE | 6 |

JS color mapping in `static/js/chart-script.js` mirrors these IDs — keep in sync.

### PDF reports
`PdfReportController` → `PdfReportService` with `PdfPageLayout`, `PdfHeaderFooterEvent`, `PdfColorScheme`. Configuration prefix: `pdf.report.*` (`PdfReportProperties`). Max records: `pdf.report.max-records=100`.

### UDP server
Optional UDP listener controlled by `udp.server.enabled=${UDP_SERVER_ENABLED:false}`, port `udp.server.port=${UDP_SERVER_PORT:2222}`.
