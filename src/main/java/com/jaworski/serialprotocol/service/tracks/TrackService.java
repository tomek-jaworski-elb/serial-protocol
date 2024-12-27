package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.dto.CheckBoxOption;
import com.jaworski.serialprotocol.dto.LogItem;
import com.jaworski.serialprotocol.dto.ModelTrackJSDTO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class TrackService {

    private static final Logger LOG = LoggerFactory.getLogger(TrackService.class);

    private final ReadLogsFileService readLogsFileService;
    private final Map<Integer, List<LogItem>> trackMap = new HashMap<>();

    private void sortListByTimestamp() {
        trackMap.replaceAll((key, value) -> value.stream()
                .sorted(Comparator.comparingLong(LogItem::getTimestamp))
                .toList());
    }

    private void validatePosition() {
        for (var entry : trackMap.entrySet()) {
            var logItems = entry.getValue();
            var logItemsModified = new ArrayList<LogItem>();
            var iterator = logItems.iterator();
            if (iterator.hasNext()) {
                var prev = iterator.next();
                while (iterator.hasNext()) {
                    var current = iterator.next();
                    var position0 = getPosition(prev.getModelTrack());
                    var position1 = getPosition(current.getModelTrack());
                    var distance = getDistance(position0, position1);
                    if (distance < 10) {
                        logItemsModified.add(current);
                    }
                    prev = current;
                }
            }
            entry.setValue(logItemsModified);
        }
    }

    private static Position getPosition(ModelTrackJSDTO trackJSDTO) {
        return new Position(trackJSDTO.getPositionX(), trackJSDTO.getPositionY());
    }

    public Map<Integer, List<LogItem>> getModels(@NonNull CheckBoxOption checkBoxOption) {
        String minValue = checkBoxOption.getMinValue();
        String maxValue = StringUtils.equalsIgnoreCase(checkBoxOption.getMaxValue(),"24:00") ? "23:59" : checkBoxOption.getMaxValue();
        // Parse the string to a LocalTime object


        Predicate<LogItem> timeFilter = logItem -> {
            LocalTime maxTime = getTime(maxValue);
            LocalTime minTime = getTime(minValue);
            if (maxTime == null || minTime == null) {
                return false;
            }
            Instant instant = Instant.ofEpochSecond(logItem.getTimestamp());
            LocalTime dateTimeLogItem = instant.atZone(ZoneOffset.UTC).toLocalTime();
            if (minTime.getHour() <= dateTimeLogItem.getHour() && maxTime.getHour() >= dateTimeLogItem.getHour()) {
                return true;
            }
            return false;
        };

        Set<Integer> modelsId = checkBoxOption.getModels() == null ?
                Collections.emptySet() :
                checkBoxOption.getModels();

        try {
            List<String> strings = readLogsFileService.readLogs() == null ?
                    Collections.emptyList() :
                    readLogsFileService.readLogs();

            strings.stream()
                    .map(LogPatternMatcher::parseTrack)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(logItem -> modelsId.contains(logItem.getModelTrack().getModelName()))
//                    .filter(timeFilter)
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
//        sortListByTimestamp();
//        validatePosition();
        return trackMap;
    }

    @Nullable
    private LocalTime getTime(String value)  {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        try {
            return LocalTime.parse(value, formatter);

        } catch (DateTimeParseException e) {
            LOG.error("Exception on parsing time: {}", value, e);
            return null;
        }
    }

    private record Position(float positionX, float positionY) {
    }

    private static float getDistance(Position p1, Position p2) {
        float dx = p1.positionX - p2.positionX;
        float dy = p1.positionY - p2.positionY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
