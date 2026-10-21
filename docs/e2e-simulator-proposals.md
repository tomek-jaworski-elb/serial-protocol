# E2E data simulator — options and recommendation

Research note, 2026-10-21. Branch `symulator-danych-AIS`. Nothing here is implemented yet; the
document exists so that the choice can be made before any code is written.

## 1. Scope

The goal is a way to run end-to-end tests of the live-data path — serial frame in, ship on the
`/chart` page out — without the six physical ship models and without a serial cable. The feed
should be producible on Windows and Linux, ideally also from a Docker container, and it should
work both for a developer's bench and for GitHub Actions.

**"AIS" in this project is not NMEA AIS.** The application calls itself "Simple AIS", but the
wire format is a private 27/29-byte binary telemetry frame emitted by the Iława ship models
(section 3). Off-the-shelf AIS simulators produce NMEA 0183 `!AIVDM` sentences, which the
application cannot read, so they are out (section 4.6). Everything below is about simulating
*this* frame.

## 2. Injection points that exist today

| Entry point | How data gets in | What it exercises | Publishes | Gaps |
|---|---|---|---|---|
| **Serial** (`SerialController` → `SerialPortListenerImpl`) | jSerialComm reads a port whose description or descriptive name contains one of the `rs.comports` tokens (`COM1,S0`); frames are split on `13,10` | the real framing, the checksum gate, the port re-open in `SerialPortChecker` | `/rs` (raw bytes) and `/json`; `MessageTranslator` log line → tracking file → `/tracks` | `SerialPortService` enumerates ports **once, in a static initializer** — a virtual port must exist before the JVM starts. Matching ignores the system port name (`ttyS0`), only the descriptions. |
| **UDP** (`UdpServer` on `udp.server.port`, default 2222, `UDP_SERVER_PORT`) | one datagram = one frame, handed to the same `MessageTranslator` | the translator and the WebSocket fan-out | `/json` only; the tracking log line is written, so `/tracks` works | Does **not** publish `/rs`, so `/terminal` shows only JSON. Does **not** check `isCRCValid`: a frame with a bad checksum or an unknown prefix (`modelName=-1`) is pushed to every browser, which the serial path never does. Logs the payload as a UTF-8 string. |
| **In-process** (`SerialPortListenerImpl.serialEvent(...)`) | `new SerialPortEvent(port, LISTENING_EVENT_DATA_RECEIVED, bytes)`; both constructors are public and `SerialPort` is not final, so a plain Mockito mock is enough | everything after the port | same as serial | test-only; no process boundary, no timing |

Consequences:

- UDP is already a working "higher than RS" entry and is the natural transport for Docker and
  CI. It needs a small parity fix (publish `/rs`, apply the checksum gate) before it can stand in
  for the serial path in tests.
- Serial injection on a developer machine is possible with a virtual port pair, but the port has
  to exist before start-up and the `rs.comports` filter has to match its description.

## 3. The wire format the simulator has to produce

Decoded by `MessageCommon` (27 bytes) and `MessageLadyMarie` (29 bytes); the length picks the
decoder (`MessageTranslator.getDTO`). Offsets are zero-based and include the trailing CR LF,
which jSerialComm hands to the listener as part of the message.

| Bytes | Field | Encoding |
|---|---|---|
| 0–1 | model prefix | ASCII, case-insensitive: `w1` Warta, `b2` Blue Lady, `d3` Dorchester Lady, `c4` Cherry Lady, `k5` Kołobrzeg, `l6` Lady Marie |
| 2–3 | heading | ×10, uint16 big-endian, degrees |
| 4–5 | bow tug direction | ×100, uint16 BE |
| 6 | bow tug force | ×10, int8 |
| 7 | engine | int8 |
| 8 | stern tug force | ×10, int8 |
| 9–10 | stern tug direction | ×100, uint16 BE |
| 11 | rudder | ×10, int8 (so ±12.7° is the range) |
| 12 | speed | ×10, int8, knots |
| 13–16 | positionX | float32 **little-endian**, metres |
| 17–20 | positionY | float32 LE, metres |
| 21 | bow thruster | int8 |
| 22–23 | gpsQuality | ×100, uint16 BE |
| 24 | checksum | low byte of the signed sum of bytes 0–23 (`CorrectionSumCalculation`) |
| 25–26 | delimiter | `0x0D 0x0A` |

**Lady Marie (29 bytes):** identical through byte 12, then two extra bytes at 13–14 that the
decoder ignores, positionX at 15–18, positionY at 19–22, bow thruster 23, gpsQuality 24–25,
checksum at 26 over bytes 0–25, CR LF at 27–28.

**Chart axes** (`getScaledPoints` in `chart-script.js`): positionX grows towards the top of the
chart (north), positionY towards the right (east). Real traffic arrives at about 1 Hz
(`docs/LogTerminal.txt`).

Faults a simulator should be able to produce on purpose, because the application has code paths
for each and nothing exercises them:

- **CR LF inside the payload.** Any field can hit `0x0D 0x0A` (heading 333.8° is `0x0D0A`; the
  float bytes are arbitrary). The serial framing then splits one frame into a short fragment and
  a remainder; the translator throws `Message length not supported` for the fragment.
- **Glued frames** (a lost delimiter) — the `length > 27` "override message" path.
- **Bad checksum**, **unknown prefix**, **bursts** (`threads.queue-capacity=100`, UDP
  `MAX_CONCURRENT_DISPATCHES=256`), and **silence** (the chart's stale indicator).

Ready-made fixtures: `docs/LogTerminal.txt` holds 64 real frames from all six ships (51 × 27
bytes, 10 × 29, three fragments), and `ModelDTOTest` holds checksummed samples. Production
`logs/log.log` contains the same `Received delimited message: [...]` arrays. Tracking logs
(`Translated message: ModelTrackDTO(...)`) can be re-encoded, but lossily: the checksum is
recomputed and Lady Marie's bytes 13–14 are unknown.

## 4. Options

### 4.1 Standalone simulator application — `ship-sim` (recommended)

A small Java 25 console application, no Spring, in its own build under `simulator/` (own
`pom.xml`, built with `.\mvnw.cmd -f simulator/pom.xml package`). It is deliberately **not** a
module of the application's pom: the application never depends on it, the application's
`Dockerfile` and CI stay untouched, and the simulator can be moved to its own repository later
without surgery.

Libraries (checked on Maven Central 2026-10-21): picocli 4.7.7 for the CLI, jSerialComm 2.11.4
for the serial sink (the application pins 2.11.0), and for the scenario file Jackson 3 YAML
(`tools.jackson.dataformat:jackson-dataformat-yaml`, 3.2.4; the application's BOM manages
Jackson 3.1.5, either is fine) — nothing else. Java 25 features that fit a CLI and are final, not
preview: instance `main` and compact source files (JEP 512), module import declarations (JEP
511), virtual threads for one timing loop per ship. Structured concurrency is still a preview in
25 (JEP 505) and stays out.

Sources:

- `replay` — reads `Received delimited message: [...]` lines (`docs/LogTerminal.txt`,
  `logs/log.log`), tracking-log `Translated message:` lines (re-encoded), or a raw `.bin` of
  concatenated frames. Keeps the original inter-frame timing from the log timestamps, or a fixed
  rate; `--loop`, `--speed 2.0`.
- `generate` — a scenario file describing ships and a timeline. Each ship has a start pose and
  scripted changes (rudder, engine/speed, tug values); a simple kinematic model integrates
  heading and position at the frame rate (X north, Y east, knots → m/s). Deterministic
  (`--seed`), so a scenario is a fixture, not a random walk. Fault injection lives here too:
  `bad-crc`, `crlf-in-payload`, `glue-next`, `unknown-prefix`, `burst`, `silence`.
- `raw` — a single hand-written frame, for one-off checks.

Sinks (URI-style, several at once allowed):

- `udp://host:port` — one datagram per frame. The one that works from Docker and CI.
- `serial://COM5?baud=9600` or `serial:///dev/ttySIM0` — writes the frame bytes through
  jSerialComm (`SerialPort.getCommPort(path)` accepts a `/dev/pts/*` path or a symlink; see 4.4).
- `stdout` / `file://frames.bin` — produce fixtures for the application's own tests.

Sketch of the command line:

```
ship-sim replay   --from docs/LogTerminal.txt --to udp://localhost:2222 --loop
ship-sim generate --scenario scenarios/six-ships.yaml --to udp://server:2222 --duration 60s
ship-sim generate --scenario scenarios/turning-circle.yaml --to serial://COM5?baud=9600
ship-sim generate --scenario scenarios/faults.yaml --to file://src/test/resources/frames/faults.bin
```

Sketch of a scenario:

```yaml
rate: 1s
ships:
  - prefix: w1              # Warta, 27-byte frame
    start: { x: 55.0, y: 60.0, heading: 230.0, speed: 2.0 }
    timeline:
      - at: 20s  { rudder: 10.0 }
      - at: 60s  { rudder: 0.0, engine: 50 }
  - prefix: l6              # Lady Marie, 29-byte frame
    start: { x: -200.0, y: 180.0, heading: 105.0, speed: 9.0 }
faults:
  - at: 30s  { ship: w1, kind: bad-crc }
  - at: 45s  { ship: l6, kind: crlf-in-payload }
```

Running it:

- **Windows / Linux, natively:** `java -jar ship-sim.jar ...` — both sinks available.
- **Docker:** multi-stage `simulator/Dockerfile` (Maven build stage → `eclipse-temurin:25-jre`),
  a `simulator` service added to the existing CI compose file under `profiles: ["e2e"]` (Compose
  profiles are the current way to add optional services; a third compose file is not needed),
  started with `docker compose --profile e2e up`, sending to `udp://server:2222` on the compose
  network. **Only the UDP sink is usable from a container:** a Linux pseudo-terminal cannot be shared between containers without `--device`
  tricks, and Docker Desktop on Windows has no COM-port passthrough at all. Serial testing is a
  native-JAR job; Docker means UDP. That is also why the UDP parity fix in section 2 comes first.
- **CI:** compose up (application + MariaDB + simulator), wait for the health check, then run
  the assertions of 4.3.

Codec safety: the simulator's encoder and the application's decoder are two implementations of
one format. Tie them with a golden fixture — the simulator writes `frames/golden.bin` plus the
expected decoded values, and a test in the application replays it through `MessageTranslator`.
The existing `ModelDTOTest` arrays are the first entries.

Effort: roughly two to three days for `replay` + `generate` + UDP, one more for the serial sink,
Docker image and the compose/CI wiring.

### 4.2 Python script (`tools/ship_sim.py`)

The same sources and sinks in ~200–300 lines with pyserial 3.5 and `socket`. Python 3.14.5 is on
this machine, 3.12 on the GitHub runner, and `python:3.14-slim` in Docker; pyserial 3.5 still
works on 3.13/3.14 (distribution rebuilds only).

- For: fastest to write, trivially cross-platform, no build step.
- Against: a second toolchain in a pure-Java repository, a second copy of the codec with no
  type-level link to the Java one, and a CI job that has to set up Python. Fine as a stopgap or
  for one-off bench use; not the thing to maintain long-term alongside 4.1.

### 4.3 In-JVM end-to-end tests (no external process)

Independent of the simulator and worth doing in any case, because it is the cheapest CI gate:

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `udp.server.port` set to a free port, a
  test-scope `FrameEncoder`, a `DatagramSocket` to push frames, and Spring's
  `StandardWebSocketClient` subscribed to `/json` and `/rs`. Assert the JSON, the tracking log
  line, and `/tracks` replay. This already exists in pieces (`UdpServerTest`, `ModelDTOTest`,
  `TracksPageTest`) but nothing joins them.
- The serial path without a port: call `SerialPortListenerImpl.serialEvent(new SerialPortEvent(
  mock(SerialPort.class), SerialPort.LISTENING_EVENT_DATA_RECEIVED, frame))`. Covers the checksum
  gate and the `/rs` publish, not jSerialComm's framing.
- Browser layer, optional: Playwright for Java 1.63.0 (Sept 2026) in a Maven profile `e2e`,
  asserting that `/chart` lists six ships with "Updated" timestamps and that `/terminal` shows
  frames. On the GitHub `ubuntu-24.04` image Chrome, Edge and Firefox are preinstalled; on this
  machine use `setChannel("chrome")` so no browser download is needed (the Artifactory-only
  policy may block `PLAYWRIGHT_DOWNLOAD_HOST`'s default). The claude-in-chrome limits recorded
  in memory (hidden tab, no `requestAnimationFrame`) do not apply to a headless Playwright
  browser.

What it cannot do: run on a bench, drive a physical port, or produce a long-running feed for
manual testing. Hence it complements 4.1 rather than replacing it.

### 4.4 Virtual serial ports with generic tools (no code)

Makes the real serial listener run against a fake port; useful on a bench, poor for CI.

- **Windows:** com0com 3.0.0.0 (SourceForge, 2017, `com0com-3.0.0.0-i386-and-x64-signed.zip`).
  The project has used it before — `docs/Opis RS232.txt` shows four "com0com - serial port
  emulator" ports enumerated. Risk: Microsoft's April 2026 change on Windows 11 24H2+ removes
  trust for cross-signed kernel drivers (audit mode first); a 2017 signature is exposed. Fallbacks
  are proprietary: VSPE (Eterlogic; 32-bit free, 64-bit paid), HHD Free Virtual Serial Ports
  (freemium), Virtual Serial Port Driver (commercial). Not installed on this machine; the only
  port here is `COM3`.
- **Linux:** `socat -d -d pty,raw,echo=0,link=/dev/ttySIM0 pty,raw,echo=0,link=/dev/ttySIM1`.
  jSerialComm's enumerator (`PosixHelperFunctions.c`) lists a pseudo-terminal **only** when a
  symlink directly in `/dev/` points at a `dev/pt*` target — a link in `/tmp` is invisible, and
  the entry is described as "PTY Device" / "Pseudo-Terminal Device", so `rs.comports` must
  contain `PTY` (or the filter must learn to match the system port name). Raw replay without a
  simulator: `pv -L 29 frames.bin > /dev/ttySIM0` for a crude 1 frame/s. socat is absent from
  both this machine and the GitHub runner image (`apt-get install socat` on the runner).
- Both: the port must exist before the application starts (static enumeration).

### 4.5 Hardware loop

A USB-RS232 adapter and a null-modem cable into `COM3`, with the simulator's serial sink on the
adapter. Real timing, real driver, real cable faults. Bench only.

### 4.6 Rejected

- **Off-the-shelf AIS simulators** (NMEASIM on SourceForge, NMEA Simulator X, panaaj's Chrome
  extension, `pyais` for scripting): all emit NMEA 0183 `!AIVDM`, which this application does not
  parse. Only relevant if an NMEA ingest is ever added.
- **A `sim` Spring profile inside the application** generating data in-process: hides the
  transport, exercises none of the ingest code, and ships test code in the product.

## 5. Comparison

| | 4.1 ship-sim (Java) | 4.2 Python | 4.3 In-JVM tests | 4.4 Virtual ports | 4.5 Hardware |
|---|---|---|---|---|---|
| Windows native | yes (UDP + serial) | yes | yes | com0com, signing risk | yes |
| Linux native | yes (UDP + serial) | yes | yes | socat | yes |
| Docker | yes, UDP only | yes, UDP only | n/a | awkward | no |
| GitHub Actions | yes, UDP | yes, UDP | yes | fragile | no |
| Exercises serial framing | with serial sink | with serial sink | no | yes | yes |
| Exercises UDP path | yes | yes | yes | no | no |
| Browser-level assertions | with 4.3 | with 4.3 | Playwright | manual | manual |
| Generated + file replay | both | both | fixtures | file only | both via sim |
| Toolchains added | none | Python | none | drivers | hardware |
| Effort to MVP | 2–3 days | 1 day | 1–2 days | hours | hours |
| Long-term maintenance | one codec + golden test | second codec | low | n/a | n/a |

## 6. Recommendation

Build **4.1 (ship-sim)** as the feed and **4.3 (in-JVM tests)** as the CI gate; use **UDP** as the
transport wherever Docker or CI is involved and the serial sink natively on a bench with 4.4 or
4.5. Keep 4.2 in reserve for a quick bench hack only.

The implementation plan is [`e2e-simulator-plan.md`](e2e-simulator-plan.md). It was reshaped
by a decision interview with the owner on 2026-10-21 and is the current word wherever the two
differ: the goal is a person verifying `/chart`, not CI; stage 0 turns the ingest path into one
`FrameIngress` pipeline; the motion model is Nomoto first order with parameters fitted from real
tracking logs; the serial sink, Docker, CI and Playwright stages are dropped; the golden file
lives in the application; there is no `--seed`.

Stages, each a separate step for review before the next one starts:

0. **Application prep (small, independent):** `UdpPacketHandlerImpl` publishes `/rs` and drops
   frames that fail the checksum, so the UDP path behaves like the serial one; optionally
   `SerialController` also matches `rs.comports` against the system port name. Unit tests for
   both.
1. **`simulator/` skeleton:** codec with golden fixtures (`ModelDTOTest` arrays + Lady Marie),
   `replay` from `docs/LogTerminal.txt` to UDP, fat JAR. First visible result: six ships moving
   on `/chart` from a replay, with no hardware.
2. **`generate`:** scenario file, kinematics, fault injection; the golden-fixture round trip in
   the application's test suite.
3. **Serial sink** plus a short how-to for com0com and socat.
4. **Docker + CI:** `simulator/Dockerfile`, the `simulator` service behind an `e2e` Compose
   profile, a CI job that starts the stack, feeds a scenario and runs the 4.3 assertions (a
   WebSocket probe first; Playwright as a follow-up). While touching `ci.yml`, move from
   `actions/checkout@v4` / `actions/setup-java@v4` to the current majors (v7 / v6). A
   `ComposeContainer`-based JUnit variant (Testcontainers 2.0.5, the version the Spring Boot
   4.1.1 BOM manages; 2.x artifacts carry the `testcontainers-` prefix, e.g.
   `org.testcontainers:testcontainers-mariadb`) is the alternative if the tests should own the
   lifecycle.

## 7. Environment facts gathered for this note

| | This machine (Windows 11) | GitHub `ubuntu-24.04` runner |
|---|---|---|
| Docker | **not installed** | Docker Compose 2.38.2 |
| socat | no | no (apt) |
| Python | 3.14.5 | 3.12.3 (+ cached 3.13/3.14) |
| Java | project wrapper, JDK 25 | 8, 11, 17, 21, 25 |
| Serial ports | `COM3` only; no com0com | none |
| Browsers for Playwright | Chrome, Edge (use `channel`) | Chrome 155, Chromium, Edge, Firefox |

So the Docker and CI stages of section 6 can only be verified in CI or on another machine; the
native JAR and the in-JVM tests can be verified here.

## 8. Verification against current practice (2026-10-21)

Checked after the recommendation was written, against primary sources where possible.

| Claim in this note | Status | Evidence |
|---|---|---|
| A standalone device simulator with a scenario file and built-in fault injection is the accepted way to test an ingest pipeline without hardware | aligned | Azure's open-source IoT Device Telemetry Simulator follows the same shape and is meant for CI; the IoT-testing literature notes that simulators rarely ship fault injection, so it has to be designed in — which 4.1 does |
| Testcontainers 2.x, `ComposeContainer` | aligned | Spring Boot 4.1.1 BOM manages `testcontainers.version=2.0.5`; 2.0.0 renamed modules to `testcontainers-<name>` and dropped JUnit 4, `ComposeContainer` remains |
| Playwright for Java 1.63.0 over Selenium | aligned | 2026 comparisons make Playwright the default for greenfield suites; Selenium only for legacy browsers or an existing Selenium estate |
| picocli 4.7.7 | aligned, with a note | still the latest; last release April 2025, so 18 months quiet. Stable and GraalVM-documented; no 5.0 in sight |
| jSerialComm 2.11.4 | aligned | 2.11.3 (Oct 2025) carried the substantive fixes; the application could bump from 2.11.0 at the same time |
| Java 25 for the CLI | aligned | JEP 512 / 511 / 519 are standard in 25; JEP 505 still preview |
| A new `docker-compose.e2e.yml` | **corrected** | Docker's preferred mechanism is a service `profiles:` list in one file; the canonical file name is `compose.yaml` (`docker-compose.yml` is kept for backwards compatibility). Section 4.1 and stage 4 now use an `e2e` profile |
| `actions/checkout@v4`, `actions/setup-java@v4` in `ci.yml` | **outdated** | current majors are checkout v7 (June 2026) and setup-java v6 (Aug 2026, ESM, Java 25 tested). Folded into stage 4 |
| Maven wrapper 3.9.6 | fine | Maven 4 is still at 4.0.0-rc-7 (Oct 2026); staying on the 3.9 line is correct |
| Golden fixtures instead of a shared codec module | trade-off, kept | the textbook answer is a multi-module build with a `codec` artifact used by both; it would move the application into a subdirectory and touch the Dockerfile, CI and every documented command. Revisit if the frame format starts to change |
| Maven project rather than a JBang script | kept | JBang 0.142.0 (Sept 2026) would make the simulator a single `//DEPS` file, but adds a tool to install on every machine; the Maven wrapper is already there |
| GraalVM native image for the CLI | not pursued | picocli supports it, but jSerialComm's JNI needs extra configuration and a 1 Hz feed gains nothing from instant start-up |

Two related modernisations are outside this note's scope but came up while checking:

- **Spring Boot's Docker Compose support** (`spring-boot-docker-compose`, optional dependency)
  starts the compose stack on `spring-boot:run` and wires MariaDB through a service connection
  matched by image name; `spring.docker.compose.profiles.active=e2e` would start the simulator
  with it. Skipped in tests unless `spring.docker.compose.skip.in-tests=false`. Useful on a
  machine with Docker — not this one.
- **H2 in tests.** The 2026 consensus is Testcontainers with the production engine instead of
  H2. The 4.3 tests do not touch the database, so H2 is not a problem for them; replacing it in
  the rest of the suite is a separate decision.

## 9. Sources

- jSerialComm port enumeration: https://raw.githubusercontent.com/Fazecast/jSerialComm/master/src/main/c/Posix/PosixHelperFunctions.c
- jSerialComm `getCommPort` / class modifiers: https://raw.githubusercontent.com/Fazecast/jSerialComm/master/src/main/java/com/fazecast/jSerialComm/SerialPort.java
- jSerialComm `SerialPortEvent` constructors: https://raw.githubusercontent.com/Fazecast/jSerialComm/master/src/main/java/com/fazecast/jSerialComm/SerialPortEvent.java
- Versions (Maven Central metadata): Playwright 1.63.0, Testcontainers 2.0.5, picocli 4.7.7, jSerialComm 2.11.4, spring-boot-testcontainers 4.1.1
- Playwright channels and download host: https://playwright.dev/java/docs/browsers
- com0com releases: https://sourceforge.net/projects/com0com/files/com0com/
- com0com signing history: https://pete.akeo.ie/2011/07/com0com-signed-drivers.html
- Windows 11 cross-signed driver trust change: https://www.elevenforum.com/t/microsoft-advancing-windows-11-driver-security-removing-trust-for-cross-signed-driver-program.45625/latest
- GitHub runner image contents: https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/Ubuntu2404-Readme.md
- NMEA AIS simulators (for the record): https://sourceforge.net/p/nmeasim/ , https://pypi.org/project/pyais/
- pyserial on Python 3.13/3.14: https://packages.fedoraproject.org/pkgs/pyserial/python3-pyserial/fedora-43-updates-testing.html
- Spring Boot 4.1.1 managed versions: https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom
- Testcontainers 2.0.0 breaking changes: https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.0
- Spring Boot Docker Compose and Testcontainers dev services: https://docs.spring.io/spring-boot/reference/features/dev-services.html
- Spring Boot Testcontainers in tests: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- Compose profiles: https://docs.docker.com/compose/how-tos/profiles/ ; file names: https://docs.docker.com/compose/intro/compose-application-model/
- actions/setup-java releases: https://github.com/actions/setup-java/releases ; actions/checkout releases: https://github.com/actions/checkout/releases
- picocli releases: https://github.com/remkop/picocli/releases ; JBang releases: https://github.com/jbangdev/jbang/releases
- JDK 25 JEPs: https://openjdk.org/projects/jdk/25/
- Maven releases (4.0.0 still RC): https://repo1.maven.org/maven2/org/apache/maven/apache-maven/maven-metadata.xml
- jSerialComm releases: https://github.com/Fazecast/jSerialComm/releases
- Jackson 3 YAML: https://repo1.maven.org/maven2/tools/jackson/dataformat/jackson-dataformat-yaml/maven-metadata.xml
- Device-simulator pattern: https://github.com/syedhassaanahmed/Iot-Telemetry-Simulator ; https://arxiv.org/pdf/2112.09580
- Playwright vs Selenium in 2026: https://testquality.com/selenium-vs-playwright-comparison/ ; https://dev.to/iamcollin/selenium-vs-playwright-in-2026-and-when-the-question-is-wrong-3od0
- Testcontainers instead of H2: https://testcontainers.com/guides/replace-h2-with-real-database-for-testing
