package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.dto.LogItem;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TrackService {

    private static final Logger LOG = LogManager.getLogger(TrackService.class);

    private final ReadLogsFileService readLogsFileService;


    public List<LogItem> readModel() {
        List<LogItem> logItems = new ArrayList<>();
        try {
            List<String> strings = readLogsFileService.readLogs() == null ? Collections.emptyList() : readLogsFileService.readLogs();
            for (String string : strings) {
                Optional<LogItem> logItem = LogPatternMatcher.parseTrack(string);
                if (logItem.isPresent()) {
                    LogItem item = logItem.get();
                    logItems.add(item);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read logs", e);
            throw new RuntimeException(e);
        }
        return logItems;
    }

}
