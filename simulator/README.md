# ship-sim — data simulator for the serial-protocol application

Feeds the application with the same 27/29-byte frames the Iława ship models transmit, so
`/chart`, `/terminal` and `/tracks` can be watched without the models and their cables. Frames go
in over UDP (the application's `udp.server.port`, default 2222) and enter the same pipeline as a
serial frame (`FrameIngress`). Background and decisions: [`../docs/e2e-simulator-plan.md`](../docs/e2e-simulator-plan.md).

## Requirements

- JDK 25 — the same one the application is built with. `run-sim.bat` uses `JAVA_HOME` when it is
  set, otherwise `java` from `PATH`.
- The application running with its UDP server enabled (`udp.server.enabled`, default `true`).

## Build

From the repository root, with the wrapper the application already has:

```
.\mvnw.cmd -f simulator\pom.xml verify        # tests + simulator\target\ship-sim.jar
```

This is a separate Maven build (own `pom.xml`, no Spring Boot parent), not a module of the
application's pom: the application never depends on it.

## Run

```
run-sim.bat generate --scenario simulator\scenarios\six-ships.yaml --loop
run-sim.bat generate --scenario simulator\scenarios\whole-lake.yaml
run-sim.bat generate --scenario simulator\scenarios\faults.yaml --duration 90s
run-sim.bat replay --from docs\LogTerminal.txt --loop
run-sim.bat replay --from logs\log.log --original-timing --speed 2.0
run-sim.bat replay --from docs\LogTerminal.txt --to udp://192.168.2.4:2222
run-sim.bat raw --bytes "119,49,8,-69,8,-69,0,6,0,8,-69,-123,1,50,73,-114,66,4,27,111,66,111,0,3,10,13,10" --to stdout
run-sim.bat --help
```

`run-sim.bat` builds the JAR when it is missing; `java -jar simulator\target\ship-sim.jar ...`
is the same thing without that convenience.

### `replay` — recorded frames from an application log

Reads every `Received delimited message: [...]` line the serial listener logs (`docs/LogTerminal.txt`
holds 61 frames of all six ships; any `logs/log.log` works too) and sends the frames as they were
recorded, delimiter included.

| Option | Meaning |
|---|---|
| `--from <file>` | the log to replay (required) |
| `--to <sink>` | `udp://host:port` (default `udp://localhost:2222`), `file://path`, `stdout` |
| `--rate <duration>` | fixed gap between frames, e.g. `250ms`, `1s` (default `1s`) |
| `--original-timing` | use the gaps between the log timestamps instead of `--rate` |
| `--speed <factor>` | divides every gap; `2.0` plays twice as fast |
| `--loop` | start over at the end of the file until Ctrl+C |
| `--include-fragments` | also send entries that are not 27 or 29 bytes long |

A line whose timestamp cannot be parsed keeps its frame and is paced like its predecessor.

### `generate` — ships that manoeuvre, from a scenario file

Reads a YAML scenario and emits one frame per ship per tick, moving each ship with a simple
model that has the inertia of the real ones.

| Option | Meaning |
|---|---|
| `--scenario <file>` | the scenario (required); `scenarios/` holds the shipped ones |
| `--ships <file>` | per-ship motion parameters; default: `ships.yaml` next to the scenario, if present |
| `--to <sink>` | `udp://host:port` (default `udp://localhost:2222`), `jsonl://path`, `file://path`, `stdout` |
| `--duration <duration>` | overrides the scenario's `duration` |
| `--speed <factor>` | divides the gap between ticks on a live sink; the motion itself is unchanged |
| `--loop` | start the scenario over at its end until Ctrl+C |

A scenario:

```yaml
rate: 1s                      # one frame per ship per second, like the models
duration: 600s                # optional; required for file sinks, --duration overrides it
ships:
  - prefix: w1                # w1 b2 d3 c4 k5 l6; l6 sends the 29-byte layout
    model: { K: 0.05, T: 8.0, accel-time: 20.0 }   # optional when ships.yaml lists the ship
    start: { x: 71.1, y: 59.7, heading: 45, speed: 0, engine: 6 }
    timeline:
      - at: 5s
        set: { speed: 1.8, engine: 40 }
      - at: 60s
        set: { rudder: 6 }
faults:
  - { at: 30s, ship: w1, kind: bad-crc }
  - { at: 50s, ship: w1, kind: burst, count: 20 }
  - { at: 60s, ship: w1, kind: silence, seconds: 30s }
```

`start` and `set` take the same keys: `x`, `y` (metres; X north, Y east), `heading` (degrees,
0 = north, clockwise), `speed` (knots, a setpoint the ship lags behind), `rudder` (degrees,
positive turns clockwise, at most 12.7 either way), `engine`, `bow-thruster`, `bow-tug-force`,
`bow-tug-direction`, `stern-tug-force`, `stern-tug-direction`, `gps-quality`. Values are checked
against the ranges of the wire format when the file is read; `x`, `y` and `heading` can only be
set in `start` (a timeline cannot teleport a ship). `engine` is a throttle setting in the real
models; here it is scripted and not coupled to the speed, by decision.

The motion (`ShipMotion`): yaw by Nomoto's first-order model, `r' = (K * rudder - r) / T` and
`heading += r * dt`; speed by a first-order lag to the setpoint with time constant `accel-time`;
position from the current speed and heading. `scenarios/ships.yaml` holds `K`, `T` and
`accel-time` per ship, chosen by eye: a plausible look is the requirement, not a match with
the real models. The chart shows positionX in -1156..506 and positionY in -64..1044; the
shipped scenarios stay inside that box.

Faults, each hitting one ship at one time:

| kind | what is sent | what the application does |
|---|---|---|
| `bad-crc` | the checksum byte is off by one | dropped before `/json` |
| `crlf-in-payload` | heading 333.8 degrees, so bytes 2-3 are CR LF | over UDP it decodes normally; a serial listener would split it into two rejected fragments |
| `glue-next` | this frame without its CR LF, glued to the ship's next one, sent one tick later | two 27-byte frames: dropped (`isCRCValid=false`); two Lady Marie frames: the second one is recovered |
| `unknown-prefix` | prefix `x9`, checksum valid | `modelName=-1`: `Model -1` in the chart's status line, no hull |
| `burst` | `count` copies in one tick (default 100) | nothing is dropped |
| `silence` | nothing for `seconds` (default 30s); the ship keeps moving | the "Updated" time in the tooltip grows |

Shipped scenarios: `six-ships.yaml` (all six from their recorded poses, slow manoeuvres,
10 min), `whole-lake.yaml` (the six on turning circles over the whole chart, 15 min),
`faults.yaml` (one of each fault on three ships, 2 min; also the source of the application's
`src/test/resources/frames/faults.jsonl`).

### `raw` — one hand-written frame

`--bytes` takes the frame as the signed bytes of a Java array literal, delimiter included, which
is exactly how the application logs and the `stdout` sink print them.

### Sinks

- `udp://host:port` — one datagram per frame, the way into a running application; paced at
  the scenario rate.
- `jsonl://path` — one JSON object per message with its `seconds`, `ship`, `fault` and `bytes`;
  the format of the application's fixtures in `src/test/resources/frames/`. Written at once.
- `file://path` — the raw frames concatenated into a file (created or truncated). Written at once.
- `stdout` — one Java array literal per line; paste it into a test or replay it later.

## Watching the result

- `/terminal` shows every frame twice: the raw bytes in "Parsed data" and the decoded JSON in
  "Raw data".
- `/chart` shows a hull and a heading/speed line per ship, with a packet counter. **Keep the
  chart tab in the foreground**: the page closes its `/json` socket while its tab is hidden and
  reopens it when the tab is shown again, so frames sent meanwhile are not drawn.
- `/tracks` replays the tracking log, which every origin feeds.

## The codec contract

`src/test/resources/golden/frames.jsonl` in the application holds recorded frames together with
what the application's decoder reads from them. The application's `GoldenFramesTest` checks the
decoder against it; this build's `FrameEncoderTest` checks that `FrameEncoder` reproduces every
frame byte for byte. The file is regenerated only by the application's `GoldenFramesWriter`
(`.\mvnw.cmd -Dtest=GoldenFramesWriter -Dgolden.write=true test`). The test finds the file at
`../src/test/resources/golden/frames.jsonl`; `-Dgolden.file=<path>` points it elsewhere.

## What is deliberately not here

No serial sink (tests enter the application above the port, and a person watches `/chart` fed
over UDP), no Docker image or CI job, and no fitting of the motion parameters to real tracking
logs - the motion only has to look plausible. The reasons are recorded in
[`../docs/e2e-simulator-plan.md`](../docs/e2e-simulator-plan.md), "Dropped, and why".
