package com.jaworski.shipsim;

import com.jaworski.shipsim.generate.ScenarioRunner;
import com.jaworski.shipsim.generate.ScenarioRunner.Tick;
import com.jaworski.shipsim.scenario.MotionParams;
import com.jaworski.shipsim.scenario.Scenario;
import com.jaworski.shipsim.scenario.ScenarioLoader;
import com.jaworski.shipsim.sink.Sink;
import com.jaworski.shipsim.sink.SinkUri;
import com.jaworski.shipsim.sink.TaggedFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;

/** Generates frames from a scenario: ships that manoeuvre with inertia, plus deliberate faults. */
@Command(name = "generate", mixinStandardHelpOptions = true,
        description = "Generates frames from a scenario file (ships, timeline, faults).")
public class GenerateCommand implements Callable<Integer> {

    static final String SHIP_PARAMETERS_FILE = "ships.yaml";

    @Option(names = "--scenario", required = true, paramLabel = "<file>",
            description = "Scenario YAML (see simulator/scenarios/).")
    Path scenario;

    @Option(names = "--ships", paramLabel = "<file>",
            description = "Per-ship motion parameters (default: ships.yaml next to the scenario, if present).")
    Path ships;

    @Option(names = "--to", defaultValue = SinkUri.DEFAULT, paramLabel = "<sink>",
            description = "udp://host:port, jsonl://path, file://path or stdout (default: ${DEFAULT-VALUE}).")
    String to;

    @Option(names = "--duration", paramLabel = "<duration>",
            description = "Overrides the scenario's duration, e.g. 90s or 10m.")
    String duration;

    @Option(names = "--speed", defaultValue = "1.0", paramLabel = "<factor>",
            description = "Divides the gap between ticks on a live sink; the motion itself is unchanged (default: ${DEFAULT-VALUE}).")
    double speed;

    @Option(names = "--loop", description = "Start the scenario over at its end until interrupted.")
    boolean loop;

    private final PrintStream err = System.err;

    @Override
    public Integer call() throws Exception {
        Map<String, MotionParams> defaults = loadShipParameters();
        Scenario plan = ScenarioLoader.load(scenario, defaults);
        if (duration != null) {
            plan = plan.withDuration(Durations.parse(duration));
        }
        Duration gap = Durations.scale(plan.rate(), speed);
        long sent = 0;
        try (Sink sink = SinkUri.open(to)) {
            if (!sink.paced() && plan.duration() == null) {
                err.println("A duration is required to write " + sink + ": set 'duration' in the scenario or pass --duration");
                return 1;
            }
            if (loop && !sink.paced()) {
                err.println("--loop needs a live sink such as udp://; " + sink + " would be written without end");
                return 1;
            }
            err.println("Generating " + plan.ships().size() + " ship(s) from " + scenario + " to " + sink
                    + (plan.duration() == null ? ", until interrupted" : ", for " + plan.duration().toSeconds() + " s")
                    + (loop ? ", looping" : ""));
            Pacer pacer = new Pacer();
            do {
                ScenarioRunner runner = new ScenarioRunner(plan);
                while (runner.hasNext()) {
                    if (sink.paced()) {
                        pacer.next(gap);
                    }
                    Tick tick = runner.next();
                    for (TaggedFrame frame : tick.frames()) {
                        sink.send(frame);
                        sent++;
                    }
                }
            } while (loop);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        err.println("Sent " + sent + " messages");
        return 0;
    }

    private Map<String, MotionParams> loadShipParameters() throws Exception {
        Path file = ships;
        if (file == null) {
            Path beside = scenario.toAbsolutePath().getParent().resolve(SHIP_PARAMETERS_FILE);
            if (Files.exists(beside)) {
                file = beside;
            }
        }
        return file == null ? Map.of() : ScenarioLoader.loadShipParameters(file);
    }
}
