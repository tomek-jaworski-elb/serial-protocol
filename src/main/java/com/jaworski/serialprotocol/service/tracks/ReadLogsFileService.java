package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ReadLogsFileService {

    private static final Logger LOG = LogManager.getLogger(ReadLogsFileService.class);
    private final Resources resources;
    private static final String TRACKING_SUFFIX = ".log";

    public List<String> readLogs() throws IOException {
//        String uri = "D:\\github\\serial-protocol\\logs\\tracking.log";
        URI uri = getUri().normalize();
        Path path = Paths.get(uri);
        if (!Files.exists(path)) {
            LOG.error("File does not exist: {}", uri);
            return Collections.emptyList();
        }
        return Files.readAllLines(path);
    }

    private URI getUri() {
        String logFilePath = resources.getLogFilePath();
        String trackingLogFileName = resources.getTrackingLogFileName() + TRACKING_SUFFIX;
        return Paths.get(".", logFilePath, trackingLogFileName).toUri();
    }

}
