package com.jaworski.shipsim.sink;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Concatenates the raw frames into a file, delimiters included. The file is created or
 * truncated when the sink opens, so a run always produces exactly what it sent.
 */
public final class FileSink implements Sink {

    private final Path path;
    private final OutputStream out;

    public FileSink(Path path) throws IOException {
        this.path = path;
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        this.out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    @Override
    public void send(byte[] frame) throws IOException {
        out.write(frame);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public String toString() {
        return "file://" + path;
    }
}
