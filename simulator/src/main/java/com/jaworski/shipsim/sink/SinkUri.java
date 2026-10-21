package com.jaworski.shipsim.sink;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Turns the {@code --to} argument into a {@link Sink}.
 *
 * <ul>
 *   <li>{@code udp://host:port} - the default is {@value #DEFAULT}, the application's UDP entry
 *       on the same machine;</li>
 *   <li>{@code file://path} - raw frames appended one after another ({@code file:///tmp/x.bin},
 *       {@code file://C:/x.bin} and {@code file://relative.bin} are all accepted);</li>
 *   <li>{@code jsonl://path} - one JSON line per message with its ship, fault and time, for test
 *       fixtures;</li>
 *   <li>{@code stdout} - one Java array literal per frame.</li>
 * </ul>
 */
public final class SinkUri {

    public static final String DEFAULT = "udp://localhost:2222";
    public static final int DEFAULT_UDP_PORT = 2222;

    private static final String UDP = "udp://";
    private static final String FILE = "file://";
    private static final String JSONL = "jsonl://";
    private static final String STDOUT = "stdout";

    private SinkUri() {
    }

    public static Sink open(String uri) throws IOException {
        String value = uri == null ? "" : uri.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals(STDOUT)) {
            return new StdoutSink(System.out);
        }
        if (lower.startsWith(UDP)) {
            return openUdp(value.substring(UDP.length()));
        }
        if (lower.startsWith(FILE)) {
            return new FileSink(filePath(value.substring(FILE.length())));
        }
        if (lower.startsWith(JSONL)) {
            return new JsonlSink(filePath(value.substring(JSONL.length())));
        }
        throw new IllegalArgumentException("unsupported sink '" + uri + "': use udp://host:port, jsonl://path, file://path or stdout");
    }

    private static Sink openUdp(String hostPort) throws IOException {
        if (hostPort.isEmpty() || hostPort.startsWith(":")) {
            throw new IllegalArgumentException("udp sink needs a host: udp://host:port");
        }
        int colon = hostPort.lastIndexOf(':');
        String host = colon < 0 ? hostPort : hostPort.substring(0, colon);
        int port = DEFAULT_UDP_PORT;
        if (colon >= 0) {
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("udp sink port is not a number: " + hostPort, e);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("udp sink port out of range: " + port);
            }
        }
        return new UdpSink(host, port);
    }

    /** {@code /C:/x} (a Windows path behind {@code file://}) loses its leading slash; anything else is taken as is. */
    static Path filePath(String rest) {
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("file sink needs a path: file://path");
        }
        if (rest.length() > 2 && rest.charAt(0) == '/' && Character.isLetter(rest.charAt(1)) && rest.charAt(2) == ':') {
            return Path.of(rest.substring(1));
        }
        return Path.of(rest);
    }
}
