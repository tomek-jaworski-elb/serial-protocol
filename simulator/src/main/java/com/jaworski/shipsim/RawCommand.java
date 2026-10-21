package com.jaworski.shipsim;

import com.jaworski.shipsim.sink.Sink;
import com.jaworski.shipsim.sink.SinkUri;
import com.jaworski.shipsim.source.LogReplaySource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/** Sends one hand-written frame, given as the signed bytes of a Java array literal. */
@Command(name = "raw", mixinStandardHelpOptions = true,
        description = "Sends a single frame given as comma-separated signed bytes.")
public class RawCommand implements Callable<Integer> {

    @Option(names = "--bytes", required = true, paramLabel = "<bytes>",
            description = "The frame, e.g. \"119,49,8,-69,...,13,10\" (delimiter included).")
    String bytes;

    @Option(names = "--to", defaultValue = SinkUri.DEFAULT, paramLabel = "<sink>",
            description = "udp://host:port, file://path or stdout (default: ${DEFAULT-VALUE}).")
    String to;

    @Override
    public Integer call() throws Exception {
        byte[] frame = LogReplaySource.parseArray(bytes.replace("[", "").replace("]", ""));
        if (frame.length == 0) {
            System.err.println("--bytes is empty");
            return 1;
        }
        try (Sink sink = SinkUri.open(to)) {
            sink.send(frame);
            System.err.println("Sent " + frame.length + " bytes to " + sink);
        }
        return 0;
    }
}
