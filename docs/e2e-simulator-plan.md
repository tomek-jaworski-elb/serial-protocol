# E2E simulator — implementation plan

Plan for the recommendation in [`e2e-simulator-proposals.md`](e2e-simulator-proposals.md)
section 6, reshaped by a decision interview with the owner on 2026-10-21 (section 1). Branch
`symulator-danych-AIS`. Status: stages 0, 1 and 2 are implemented and accepted on screen
(commits `331835d`, `5599a03`, `0389b4b`); everything listed under "Dropped, and why" is not.

## 0. Ground rules

- **One stage at a time.** Each stage ends with the acceptance named in it and waits for
  approval; the next one does not start on the strength of the branch existing or this plan
  being written.
- **No commits or pushes** unless asked for explicitly. New commits, never amend.
- Code, comments, documentation and commit messages in English; `README.md` stays Polish, so
  its pointer to the simulator is Polish too. No copyright headers (private project).
- **Definition of done:** `.\mvnw.cmd clean test` green; from stage 1 on also
  `.\mvnw.cmd -f simulator/pom.xml verify` green; documentation changed in the same change —
  `CLAUDE.md`, and the `AGENTS.md` mirror whenever one of the three mirrored blocks
  (**commands**, **invariants**, **conventions**) changes; for stages 1 and 2, the on-screen
  acceptance described there.
- **Verification on this machine:** unit and in-JVM tests, the simulator JAR against a
  throwaway UDP listener, and — with the application started locally by the owner (MariaDB
  listens on `127.0.0.1:3306`, `run.bat` starts the JAR) — the `/terminal` and `/chart` pages
  in Chrome.

## 1. Decisions from the interview (2026-10-21)

| Topic | Decision |
|---|---|
| Purpose | A person verifies the result on `/chart`; automated end-to-end in CI is out of scope |
| Where the application runs | Locally on the owner's laptop, fed over UDP at `localhost:2222` |
| Simulator-only code in the application | The UDP entry stays behind its existing property, `udp.server.enabled` (`UDP_SERVER_ENABLED`, default `true`, unchanged by decision of 2026-10-21); the shared pipeline itself is production code and is not gated |
| Visual acceptance | The `/chart` tab must be in the foreground while frames arrive: `chart-script.js` closes its `/json` socket when the tab is hidden and reopens it when shown (found during the stage 0 check) |
| Simulator location and stack | `simulator/` in this repository, own pom, Java 25 + picocli, version `1.0` |
| Stage 0 | The ingest pipeline becomes one `FrameIngress` interface with one implementation; serial and UDP are adapters. No change to the `rs.comports` port selection |
| Golden file | In the application; the simulator reads it through a property |
| Replay sources | Raw `Received delimited message: [...]` lines only; no re-encoding of tracking logs |
| Motion model | Nomoto first-order yaw response, parameters per ship; speed follows the scripted setpoint with a lag; `engine` (a throttle setting) is scripted independently — no coupling between frame fields |
| Parameters | Chosen by eye in `scenarios/ships.yaml`. The `fit` subcommand and the delivery of real tracking logs were dropped on 2026-10-21 after the generator's acceptance: the owner does not need the motion to reproduce the real models |
| Scenarios in v1 | `six-ships.yaml` (start poses from the real log) and `whole-lake.yaml` (spread over the map); all six fault kinds |
| Convenience | Default sink `udp://localhost:2222`; `run-sim.bat` next to `run.bat` |
| Acceptance | Stage 0 by tests; stages 1 and 2 on screen with the owner |
| Delivery | Stage 0 as its own pull request; then one commit per approved stage and one pull request for the simulator after stage 2 |
| Dropped | Serial sink, Docker image and CI job, Playwright, jSerialComm bump in the application, system-name match in `rs.comports`, a CI job for the simulator's unit tests |

## Stage 0 — one ingest pipeline behind `FrameIngress` (own pull request)

**Goal.** Every frame, whatever brought it in, goes through one pipeline, and tests have a
single in-process entry point above the port.

**Today.** `SerialPortListenerImpl.serialEvent` publishes `Arrays.toString(bytes)` to `/rs`,
translates, and publishes `/json` only when `isCRCValid()`; `UdpPacketHandlerImpl.handle`
translates and publishes `/json` unconditionally, never publishes `/rs`, and logs the payload as
a UTF-8 string. Two copies of one pipeline, already diverged.

**Change.**

- New interface `FrameIngress` (`service.ingest`) with one method, `ingest(byte[] frame,
  String origin)`, and one implementation, `FrameIngestService`, which in this order: publishes
  `Arrays.toString(frame)` to `SessionType.RS` (the text the `parseData` area on `/terminal`
  shows); calls `MessageTranslator.getDTO`; if `!isCRCValid()`, logs at INFO "Failed to
  translate message" with the origin and stops; otherwise serialises through
  `JsonMapperService` and publishes to `SessionType.JSON`. `IllegalArgumentException` from an
  unsupported length and `JacksonException` are caught and logged, as the serial listener does
  today. `getDTO` never returns `null` (it throws), so no null check.
- `SerialPortListenerImpl.serialEvent` keeps the port log line and delegates to `ingest(data,
  portDescription)`. `UdpPacketHandlerImpl.handle` keeps `supports()` (length > 2) and
  delegates to `ingest(payload, sender)`; its UTF-8 payload log goes.
- The "Translated message: ..." log line stays inside `MessageTranslator.getDTO`, so the
  tracking file and `/tracks` are untouched and now fed by every origin.

**Deliberately not done.** No `modelName == -1` check — the serial path has none, and the chart
copes with an unknown id (it prints `Model -1` in the status line and draws no hull because
`modelsConfig[-1]` is undefined). No change to `SerialController` port matching: without a
serial sink there is no virtual port to select.

**Tests** (Mockito, no Spring context, fixtures from `ModelDTOTest` and the Lady Marie frames in
`docs/LogTerminal.txt`):

- `FrameIngestServiceTest`: valid 27-byte frame → `/rs` then `/json` (`InOrder`); checksum byte
  changed → `/rs`, `/json` `never()`; valid 29-byte Lady Marie → `/json`; 4-byte fragment →
  `/rs`, no `/json`, no exception escapes; two 27-byte frames glued by a lost CR LF into 52
  bytes → no `/json` (the translator returns `isCRCValid=false`); unknown prefix `x9` with a
  valid checksum →
  `/json` with `modelName=-1`.
- `SerialPortListenerImplTest`: a real `SerialPortEvent(mock(SerialPort.class),
  LISTENING_EVENT_DATA_RECEIVED, bytes)` (both constructors are public, `SerialPort` is not
  final) reaches `ingest` with the port description as origin.
- `UdpPacketHandlerImplTest`: a packet reaches `ingest` with the sender as origin; a two-byte
  payload is not supported.

**Docs.** `CLAUDE.md`: the "Serial ingest pipeline" diagram gains `FrameIngress` as the join
point of the serial and UDP branches, and the UDP section says it now publishes `/rs` and
applies the checksum gate; one new line in the **invariants** block, mirrored in `AGENTS.md`:
every frame enters through `FrameIngress`; adapters never publish `/rs` or `/json` themselves.

**Acceptance.** Tests green. Optional on-screen check with the application running: one frame
sent by UDP appears in both areas of `/terminal` and as a hull on `/chart`.

**Estimate.** Half a day to a day.

## Stage 1 — `simulator/` skeleton: codec, golden fixtures, replay to UDP

**Goal.** All six ships appear on `/chart` from a replay of `docs/LogTerminal.txt`, with no
hardware. In that recording four ships hold one position and the other two move by a few
metres, so "appear" is the right word; movement comes with the generator in stage 2.

**Layout** (all new):

```
simulator/
  pom.xml                                   com.jaworski:ship-sim 1.0, Java 25, own build
  README.md                                 usage, sinks, scenarios (grows in stage 2)
  run-sim.bat                               builds the JAR if missing, runs the given command
  src/main/java/com/jaworski/shipsim/
    ShipSim.java                            picocli root command; subcommands replay, raw
    codec/Frame.java                        record: prefix + the fields of proposals section 3
    codec/FrameEncoder.java                 27/29-byte layout, checksum
    source/LogReplaySource.java             "Received delimited message: [...]" lines
    sink/Sink.java                          interface: send(byte[] frame)
    sink/UdpSink.java                       DatagramChannel, one datagram per frame
    sink/StdoutSink.java, sink/FileSink.java  (JsonlSink joins in stage 2)
    sink/SinkUri.java                       udp://host:port (default udp://localhost:2222),
                                            file://path, stdout
  src/test/java/com/jaworski/shipsim/...    FrameEncoderTest, LogReplaySourceTest,
                                            UdpSinkTest, SinkUriTest
```

**Build.** A separate pom, not a module of the application's pom (reasons in proposals 4.1).
It has no parent, so it manages everything itself: `tools.jackson:jackson-bom` 3.2.4 imported
in `dependencyManagement` (the application runs Jackson 3.1.5 through Spring Boot; the builds
are separate and need not agree), `org.junit:junit-bom` 6.0.3 (the version Spring Boot 4.1.1
manages), maven-compiler-plugin with `<release>25</release>` and maven-surefire-plugin, both
pinned explicitly (maven-compiler-plugin 4.0.0 is still a beta, so the current 3.x; surefire
the same version the application's build resolves). Dependencies: picocli 4.7.7 and
`jackson-databind` (for the golden file). Fat JAR with maven-shade-plugin 3.6.2 using the
`ManifestResourceTransformer` (`Main-Class`) and the `ServicesResourceTransformer` (Jackson
registers modules through `META-INF/services`), `finalName` `ship-sim`, so the artifact is
`simulator/target/ship-sim.jar`. Built from the repository root with the existing wrapper:
`.\mvnw.cmd -f simulator/pom.xml verify`. YAML joins in stage 2; jSerialComm is not needed.

**Codec.** `Frame` is a record with every field of proposals section 3 plus `reserved` — the two
bytes at offsets 13-14 of the 29-byte layout, which the application's decoder skips but which
must be reproduced for byte-exact fixtures. `FrameEncoder` writes scaled integers with
`Math.round` (256.94 x 100 is not an exact double), positions as float32 little-endian, and the
checksum as the low byte of the signed sum, exactly as `CorrectionSumCalculation.calculateLowerByte`
computes it.

**Golden fixture — the contract between the two builds.** One file, in the application because
its decoder is the source of truth: `src/test/resources/golden/frames.jsonl`, one JSON object per
line with the frame bytes and every decoded field. Generated once from the `ModelDTOTest` arrays
and the ten Lady Marie frames in `docs/LogTerminal.txt`, then committed. Two details that make
byte-equality possible:

- positions are stored twice: `positionX` as the application returns it (rounded to two decimals
  by `MessageCommon.getPositionX`) and `positionXRaw` as `Float.toString` of the exact float in
  the bytes, which is what the encoder is given;
- Lady Marie lines carry `reserved`.

How the file is produced: a test-scope class in the application, `GoldenFramesWriter`, run on
demand (`.\mvnw.cmd -Dtest=GoldenFramesWriter -Dgolden.write=true test`), decodes the fixture
arrays through `MessageTranslator` and writes the lines, including `positionXRaw` read straight
from the bytes. It is a tool, not a test: without the property it does nothing.

Tests on both sides: `GoldenFramesTest` in the application decodes each line's bytes through
`MessageTranslator` and compares the rounded fields; `FrameEncoderTest` in the simulator encodes
each line's fields and compares the bytes. The simulator test takes the path from the system
property `golden.file`, default `../src/test/resources/golden/frames.jsonl` (Maven runs tests
with the module directory as the working directory), and fails with a message naming the path
if the file is missing.

**Replay source.** Parses lines matching `Received delimited message: [` ... `]` into byte arrays
and the leading timestamp (`dd-MM-yyyy HH:mm:ss.SSS`, as in `docs/LogTerminal.txt`); lines
without a frame are ignored. A frame whose timestamp does not parse — the first line of that
file is truncated to `0-07-2024 ...` — is kept, logged once as a warning, and paced like its
predecessor (sent at once if it is the first). Frames of 27 or 29 bytes are replayed as they
are — they already end in CR LF. Other lengths (the three fragments in that file) are skipped
unless `--include-fragments` is given. Pacing: `--rate 1s` (default) or `--original-timing` from
the timestamps; `--speed 2.0` divides the gaps; `--loop` restarts at the end.
`LogReplaySourceTest` runs over the real `docs/LogTerminal.txt` and expects 61 frames (64 with
fragments) covering all six prefixes.

```
run-sim.bat replay --from docs\LogTerminal.txt --loop
java -jar simulator/target/ship-sim.jar replay --from logs/log.log --to udp://192.168.2.4:2222 --original-timing
java -jar simulator/target/ship-sim.jar raw --bytes "119,49,8,-69,..." --to stdout
```

**Docs.** `CLAUDE.md`: a "Simulator" section (what it is, that the golden file is a contract,
the default UDP target) and two lines in the commands block — build and replay — mirrored into
`AGENTS.md`; one invariants line: `golden/frames.jsonl` is shared with `simulator/`.
`README.md`: a short pointer in Polish. `simulator/README.md`: usage.

**Acceptance, on screen.** The owner starts the application locally and keeps the `/chart` tab
in the foreground; I run the replay from this machine and check in Chrome: six hulls on `/chart` with the ship list showing an "Updated"
time for each, frames in both areas of `/terminal`, and `/tracks` able to show the replayed
positions for the day. Before that, the JAR pointed at a throwaway UDP listener must emit
datagrams byte-identical to the arrays in the log.

**Estimate.** One to one and a half days.

## Stage 2 — `generate`: scenarios, Nomoto motion, fault injection

**Goal.** Ships that manoeuvre on `/chart` the way the Iława models do — visible inertia in
turning and in speed changes — from a scenario file, plus deliberate faults.

**Scenario file** (YAML through `tools.jackson.dataformat:jackson-dataformat-yaml`, bound to
records):

```yaml
rate: 1s
duration: 120s
ships:
  - prefix: w1                 # Warta; l6 produces the 29-byte layout
    model: { K: 0.05, T: 8.0, accel-time: 20.0 }   # per ship, see scenarios/ships.yaml
    start: { x: 71.1, y: 59.7, heading: 232.5, speed: 0.0, rudder: 0.0, engine: 6 }
    timeline:
      - at: 10s
        set: { speed: 4.0, engine: 50 }
      - at: 40s
        set: { rudder: 10.0 }
      - at: 90s
        set: { rudder: 0.0 }
faults:
  - { at: 30s, ship: w1, kind: bad-crc }
```

(As built: a timeline entry is `at` plus a `set` map, and a fault is one flow mapping; both
bind to records without a custom deserializer.)

`model` can be omitted when the ship is listed in `scenarios/ships.yaml`, the per-ship
parameter file (values chosen by eye). `--duration` on the command line overrides
`duration` in the file. There is no `--seed`: nothing is random.

**Units and ranges**, validated by the parser, which rejects a scenario with a named field out
of range: X north and Y east in metres; heading in degrees, 0 = north, clockwise, kept in
[0, 360) after every step; speed in knots within int8 x10 (so up to 12.7, negative = astern);
rudder within 12.7 degrees either way, positive turning the heading clockwise — a convention of
the simulator, not a verified property of the models; `engine`, tug forces and bow thruster
within int8; tug directions and gpsQuality within uint16 x100. The chart shows positionX in
[-1156, 506] and positionY in [-64, 1044] — derived from `getScaledPoints`
(`y = (-positionX + 506) * mapa_x`, `x = (positionY + 64) * mapa_x`, `mapa_x = 2.407`) and the
2666 x 4000 map; shipped scenarios stay inside that box.

**Motion** — per tick `dt = rate`, computed with `StrictMath` so a committed fixture is
bit-identical on every JVM:

- yaw, Nomoto first order: `r' = (K * rudder - r) / T`, `heading += r * dt`; `K` in
  (deg/s) per degree of rudder, `T` in seconds; with `T` small and `K` large it degenerates to
  the earlier no-inertia model, so the same code covers both;
- speed, first-order lag to the scripted setpoint: `v' = (v_set - v) / accel-time`
  (`accel-time` 0 = immediate);
- position: `x += v * 0.514444 * cos(heading) * dt`, `y += v * 0.514444 * sin(heading) * dt`.

`engine` is a throttle setting in the real models, but by decision it is **not** coupled to
speed here: it is a scripted value that goes into the frame unchanged, like the tug fields.
Pacing applies to the `udp://` sink; `file://`, `jsonl://` and `stdout` write the whole scenario
at once.

**Faults** — each one documented with its byte effect and what the application does with it:

| kind | byte effect | serial path | UDP path (after stage 0) |
|---|---|---|---|
| `bad-crc` | checksum byte + 1 | dropped before `/json`, "Failed to translate message" | same |
| `crlf-in-payload` | heading forced to 333.8 degrees, so bytes 2-3 are `0D 0A` | jSerialComm splits the frame at the delimiter: a 4-byte fragment and a 23-byte remainder (25 for Lady Marie), each rejected with "Message length not supported" | one datagram, decodes normally — the fault only shows on serial |
| `glue-next` | CR LF omitted between this frame and the ship's next | two 27-byte frames arrive as one 52-byte message; `MessageTranslator` trims the last 27 bytes, finds a known prefix and returns `isCRCValid=false`, so nothing reaches `/json` — but the inner `getDTO` call has already logged "Translated message: ..." for the second frame, so it still lands in the tracking file and in `/tracks`. Two 29-byte Lady Marie frames (56 bytes): the first attempt on the last 27 bytes starts inside the heading, finds no prefix and logs a `modelName=-1` line (which `LogPatternMatcher` skips), then the `> 29` branch trims the last 29 and returns the second frame as valid — only the first is lost | same, as one datagram |
| `unknown-prefix` | prefix `x9` | reaches `/json` with `modelName=-1`; chart prints `Model -1`, draws nothing | same |
| `burst` | N frames of one ship with no pacing | nothing is dropped: the fan-out executor uses `CallerRunsPolicy`, so above `threads.queue-capacity=100` the publishing runs on the serial thread | nothing dropped below `MAX_CONCURRENT_DISPATCHES=256` in flight |
| `silence` | the ship sends nothing for T seconds | the tooltip's "Updated: N seconds ago" grows; the explicit "no data N s" warning exists only for a followed ship (`updateFollowStatus`) | same |

Rules the table implies, settled by the code reviews of 2026-10-21: a fault due while its
ship is silent stays queued and fires on the first frame after the silence (a held `glue-next`
frame waits the same way); a `glue-next` on the last tick of a run has nothing to glue to, so
the frame goes out as it is; several faults due on one tick all apply and the message is
tagged with their names joined by `+`; a second `glue-next` while a frame is held adds to it.
Live sinks are paced against a schedule (no drift, no double frame at a `--loop` wrap);
`--loop` is refused for file sinks; the UDP sink is unconnected, so an application that is not
up yet does not end the run. The UDP adapter logs the same "Received delimited message" phrase
as the serial one, so a simulated session can be replayed from `log.log`. As built, `generate`
also takes `--ships <file>` to point at a parameter file other than the `ships.yaml` beside the
scenario.

**Shipped scenarios.** `simulator/scenarios/six-ships.yaml` (all six, start poses taken from
`docs/LogTerminal.txt`, slow manoeuvres), `whole-lake.yaml` (the six spread over the map,
longer runs), `faults.yaml` (one of each fault), and `ships.yaml` (per-ship `K`, `T`,
`accel-time`, with a comment saying the values were chosen by eye and accepted on screen).

**`fit` subcommand — dropped.** It was to estimate `K` and `T` per ship from real
`tracking.log` files once the generator was accepted. At that acceptance (2026-10-21) the owner
said the motion need not reproduce the real models, so `fit` and the delivery of logs are off
the table; `ships.yaml` keeps values chosen by eye and says so.

**Tests.** Simulator: a northbound run increases X and leaves Y unchanged; an eastbound run
increases Y; a constant rudder yields a circle whose radius follows `K`; the yaw rate
approaches `K * rudder` with time constant `T`; speed approaches the setpoint with
`accel-time`; scenario parsing and range validation; each fault produces the documented bytes;
the same scenario rendered twice gives identical streams. Application:
fixtures written by the `jsonl://` sink — one line per emitted message with its bytes and the
fault kind that produced it (or none) — committed as `src/test/resources/frames/six-ships.jsonl`
and `faults.jsonl`, because a glued or split message cannot be recovered from a flat byte file.
`ScenarioFixtureTest` replays every line through `MessageTranslator` and asserts the outcome
the table above promises for that line's fault kind, and that all six ids are present in
`six-ships.jsonl`.

**Acceptance, on screen.** `run-sim.bat generate --scenario scenarios\six-ships.yaml`: six
ships manoeuvre on `/chart`, a rudder order visibly takes seconds to bend the track and a speed
order seconds to take effect, and the owner judges whether the motion looks like the models
(then `K`, `T`, `accel-time` are tuned in `ships.yaml` on the spot). `faults.yaml`: the effects
of the table — an unknown ship shows as `Model -1` in the status line, a silent ship's
"Updated" time grows, `bad-crc` frames change nothing — without disturbing the other ships.
After this stage: one pull request for `simulator/`.

**Estimate.** Two days.

## Dropped, and why

- **Serial sink and virtual port pairs** (the former stage 3): tests enter above the port
  through `FrameIngress`, and a person watches `/chart` fed over UDP; writing to a real port
  would only exercise jSerialComm itself. If the need returns, a `serial://` sink is half a
  day's work; the how-to for com0com and socat and the port-selection caveat stay in proposals
  4.4.
- **Docker image, Compose profile, CI `e2e` job, Playwright** (the former stages 4 and 5): CI
  is out of scope for this undertaking; the owner's eyes are the acceptance.
- **jSerialComm bump in the application** (2.11.0 to 2.11.4): it belonged to the serial sink.
- **`rs.comports` system-name match**: only needed to pick one end of a virtual port pair.
- **Re-encoding of tracking logs for replay**: lossy, and `log.log` carries the raw arrays.
- **A CI job for the simulator's unit tests**: not asked for; added only on request.
- **`fit` and real tracking logs** (end of stage 2): the owner accepted the generator with
  parameters chosen by eye and does not need the motion to match the real models.

## Recorded for later, without action

- Tomcat's WebSocket client (11.0.24, the version the application uses) always verifies the
  host name — `setEndpointIdentificationAlgorithm("HTTPS")` in `WsWebSocketContainer`, no
  property to disable it. A future CI probe against `wss://localhost:8081` needs a certificate
  with `SAN=dns:localhost,ip:127.0.0.1`, not a trust-all `SSLContext`; the committed
  `cert/server.p12` is `CN=www.example.com` without SAN.
- Feeding the laboratory server from a laptop needs `2222:2222/udp` in `docker-compose.yml`,
  which publishes only `8081:443` today.

## Cross-cutting

- **Golden file discipline.** Any change to the frame format changes `frames.jsonl` first, and
  both `GoldenFramesTest` and `FrameEncoderTest` must go red before the code changes.
- **Log strings stay an API.** The simulator never asks the application to change
  "Translated message: ..." or "Received delimited message: ..."; the replay source parses the
  latter, so both are consumed by something — recorded in the **invariants** block of
  `CLAUDE.md`, mirrored in `AGENTS.md`.
- **Memory note.** The project memory records the environment limits and the decisions; it
  gets the stage status after each approval.
