package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.dto.LogItem;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TrackService {

    private static final Logger LOG = LogManager.getLogger(TrackService.class);

    private final ReadLogsFileService readLogsFileService;

    public Map<Integer, List<LogItem>> getModels(@NonNull Set<Integer> modelsId) {
        Map<Integer, List<LogItem>> trackMap = new HashMap<>();
        try {
            List<String> strings = readLogsFileService.readLogs() == null ?
                    Collections.emptyList() :
                    readLogsFileService.readLogs();

            strings.stream()
                    .map(LogPatternMatcher::parseTrack)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(logItem -> modelsId.contains(logItem.getModelTrack().getModelName()))
                    .forEach(logItem -> {
                        int modelName = logItem.getModelTrack().getModelName();
                        if (!trackMap.containsKey(modelName)) {
                            trackMap.put(modelName, new ArrayList<>());
                        }
                        trackMap.get(modelName).add(logItem);
                    });

        } catch (IOException e) {
            LOG.error("Failed to read logs", e);
            return Collections.emptyMap();
        }
        return trackMap;
    }
}
