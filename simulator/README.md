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

### `raw` — one hand-written frame

`--bytes` takes the frame as the signed bytes of a Java array literal, delimiter included, which
is exactly how the application logs and the `stdout` sink print them.

### Sinks

- `udp://host:port` — one datagram per frame, the way into a running application.
- `file://path` — the raw frames concatenated into a file (created or truncated).
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

## What is not here yet

`generate` — scenarios with a Nomoto motion model, fault injection and the `fit` of its
parameters from real tracking logs — is stage 2 of the plan.
