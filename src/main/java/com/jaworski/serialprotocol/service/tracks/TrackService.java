package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.dto.LogItem;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class TrackService {

    private static final Logger LOG = LogManager.getLogger(TrackService.class);

    private final ReadLogsFileService readLogsFileService;


    public List<LogItem> readModel() {
        List<LogItem> logItems;
        try {
            List<String> strings = readLogsFileService.readLogs() == null ? Collections.emptyList() : readLogsFileService.readLogs();
            logItems = strings.stream()
                    .map(LogPatternMatcher::parseTrack)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (IOException e) {
            LOG.error("Failed to read logs", e);
            throw new RuntimeException(e);
        }
        return logItems;
    }

    public List<LogItem> getModel(@NonNull Predicate<LogItem> filter) {
       return readModel().stream()
               .filter(filter)
               .toList();
    }

}
