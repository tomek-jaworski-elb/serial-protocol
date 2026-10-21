package com.jaworski.shipsim;

import com.jaworski.shipsim.sink.Sink;
import com.jaworski.shipsim.sink.SinkUri;
import com.jaworski.shipsim.source.LogReplaySource;
import com.jaworski.shipsim.source.LogReplaySource.Entry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Replays recorded frames from an application log, at a fixed rate or with the gaps the log
 * shows between them.
 */
@Command(name = "replay", mixinStandardHelpOptions = true,
        description = "Replays 'Received delimited message: [...]' lines from an application log.")
public class ReplayCommand implements Callable<Integer> {

    @Option(names = "--from", required = true, paramLabel = "<file>",
            description = "Log file to replay (docs/LogTerminal.txt, logs/log.log).")
    Path from;

    @Option(names = "--to", defaultValue = SinkUri.DEFAULT, paramLabel = "<sink>",
            description = "udp://host:port, file://path or stdout (default: ${DEFAULT-VALUE}).")
    String to;

    @Option(names = "--rate", defaultValue = "1s", paramLabel = "<duration>",
            description = "Gap between frames, e.g. 250ms or 1s (default: ${DEFAULT-VALUE}).")
    String rate;

    @Option(names = "--original-timing",
            description = "Use the gaps between the log timestamps instead of --rate.")
    boolean originalTiming;

    @Option(names = "--speed", defaultValue = "1.0", paramLabel = "<factor>",
            description = "Divides every gap: 2.0 plays twice as fast (default: ${DEFAULT-VALUE}).")
    double speed;

    @Option(names = "--loop", description = "Start over at the end of the file until interrupted.")
    boolean loop;

    @Option(names = "--include-fragments",
            description = "Also send entries that are not 27 or 29 bytes long.")
    boolean includeFragments;

    private final PrintStream err = System.err;

    @Override
    public Integer call() throws Exception {
        List<Entry> entries = LogReplaySource.read(from, includeFragments);
        if (entries.isEmpty()) {
            err.println("No 'Received delimited message' lines in " + from);
            return 1;
        }
        Duration fixed = Durations.scale(Durations.parse(rate), speed);
        long unstamped = entries.stream().filter(e -> e.timestamp() == null).count();
        if (unstamped > 0) {
            err.println("Warning: " + unstamped + " of " + entries.size() + " frames have no parseable timestamp"
                    + (originalTiming ? " and are paced like their predecessor" : ""));
        }
        long sent = 0;
        try (Sink sink = SinkUri.open(to)) {
            err.println("Replaying " + entries.size() + " frames from " + from + " to " + sink + (loop ? ", looping" : ""));
            do {
                Entry previous = null;
                Duration delay = fixed;
                for (Entry entry : entries) {
                    if (previous != null) {
                        delay = gap(previous, entry, delay, fixed);
                        Thread.sleep(delay);
                    }
                    sink.send(entry.frame());
                    sent++;
                    previous = entry;
                }
            } while (loop);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        err.println("Sent " + sent + " frames");
        return 0;
    }

    private Duration gap(Entry previous, Entry current, Duration lastGap, Duration fixed) {
        if (!originalTiming) {
            return fixed;
        }
        if (previous.timestamp() == null || current.timestamp() == null) {
            return lastGap;
        }
        Duration recorded = Duration.between(previous.timestamp(), current.timestamp());
        return recorded.isNegative() ? Duration.ZERO : Durations.scale(recorded, speed);
    }
}
