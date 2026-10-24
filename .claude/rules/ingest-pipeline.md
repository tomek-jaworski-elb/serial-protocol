---
paths:
  - "src/main/java/**/serial/**"
  - "src/main/java/**/udp/**"
  - "src/main/java/**/thread/**"
  - "src/main/java/**/service/ingest/**"
  - "src/main/java/**/service/tracks/**"
  - "src/main/java/**/service/utils/**"
  - "src/main/java/**/service/StartUp.java"
  - "src/main/java/**/dto/Models.java"
  - "src/main/java/**/dto/ModelTrackDTO.java"
  - "src/main/resources/logback-spring.xml"
  - "src/test/java/**/{serial,udp,golden}/**"
  - "src/test/resources/frames/**"
  - "src/test/resources/golden/**"
  - "simulator/**"
  - "docs/e2e-simulator-plan.md"
  - "docs/LogTerminal.txt"
  - "docs/Opis RS232.txt"
---

# Ingest pipeline, track replay, UDP and the simulator

Loaded when you touch the serial/UDP adapters, the ingest service, track replay, the ship registry or `simulator/`. The invariants that guard this area are in `CLAUDE.md` (Log strings are an API, FrameIngress, golden file).

## Boot flow
`SerialProtocolApplication` → `StartUp.start()` opens serial ports, optionally checks external REST connectivity.

## Ingest pipeline (serial and UDP)
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

## Track replay (`/tracks`)
There is no track table. `logback-spring.xml` writes every translated frame to `logs/${logging.tracking.file.name}.log` (`SAVE-TRANSMISSION-FILE`, rolled daily into `logs/yyyy/MM/`). `ReadLogsFileService` reads those files back, `LogPatternMatcher` holds the regexes, `TrackService` rebuilds tracks. The log format *is* the storage format — treat those log strings as an API. The page receives the ship palette as the `shipColors` model attribute (`Models.colorsById()`) and reads it in `tracks.html`.

## Ship model registry
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

## UDP server
Optional UDP listener: `udp.server.enabled=${UDP_SERVER_ENABLED:true}`, port `udp.server.port=${UDP_SERVER_PORT:2222}`, buffer `udp.server.buffer-size`. When disabled, `UdpServer`, `UdpDispatcher` and `UdpPacketHandlerImpl` are not created at all (`@ConditionalOnBooleanProperty`). `UdpServer` → `UdpDispatcher` → `UdpPacketHandlerImpl` → `FrameIngress`; properties bound by `UdpProperties`. A datagram is treated exactly like a serial frame — logged as `"From UDP <sender> Received delimited message: [...]"` (the phrase the simulator's replay parses, so a UDP-fed session can be replayed from `log.log`), published raw on `/rs`, translated, dropped unless the checksum verifies, then published on `/json` — so UDP is the transport for simulated traffic. It used to skip `/rs` and the checksum gate; that is why the pipeline was pulled out of the adapters. The port has no authentication: stray datagrams are logged at WARN (no stack trace) and, like serial noise, shown raw on `/terminal`; the lab compose does not publish the port.

## Data simulator (`simulator/`)
`simulator/` is a separate Maven build (own `pom.xml`, no Spring, Java 25, picocli 4.7.7, Jackson 3), **not** a module of this pom: the application never depends on it. It feeds frames to a running application over UDP — the default sink is `udp://localhost:2222` — so they enter through `FrameIngress` like a serial frame. Build with `.\mvnw.cmd -f simulator/pom.xml verify`; `run-sim.bat` builds the JAR when missing and runs it with the JDK from `JAVA_HOME` (the JAR is compiled for Java 25, a Java 21 on `PATH` refuses it). Commands: `replay` (recorded `Received delimited message: [...]` lines; `docs/LogTerminal.txt` holds 61 frames of all six ships), `generate` (a YAML scenario in `simulator/scenarios/`: ships moved by a Nomoto first-order yaw model with `K`, `T`, `accel-time` per ship from `scenarios/ships.yaml`, scripted `engine` uncoupled from speed by decision, six fault kinds) and `raw`. `generate --to jsonl://...` wrote the application's fixtures in `src/test/resources/frames/` (`six-ships.jsonl`, `faults.jsonl`), which `ScenarioFixtureTest` replays through `MessageTranslator`; regenerate them from the same scenarios when the scenarios change. The codec contract with this application is the golden file (invariants in `CLAUDE.md`). Plan, decisions and what was dropped: [`docs/e2e-simulator-plan.md`](docs/e2e-simulator-plan.md); usage: [`simulator/README.md`](simulator/README.md). When watching `/chart`, keep its tab in the foreground — `chart-script.js` closes the `/json` socket of a hidden tab and reopens it when the tab is shown — and keep `app.runs.test-enabled` at `false`: that older flag only animates hulls in the browser (`runShipTest` in `chart-script.js`, no frames, nothing on `/terminal` or `/tracks`) and would fight the simulator for the same hulls.
