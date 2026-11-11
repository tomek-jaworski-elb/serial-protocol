package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.tracks.ShipTrack;
import com.jaworski.serialprotocol.dto.tracks.TrackCoverage;
import com.jaworski.serialprotocol.dto.tracks.TrackPoint;
import com.jaworski.serialprotocol.dto.tracks.TrackQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;

/**
 * Tracks of the chosen ships between two instants, read from the tracking log.
 *
 * <p>Stateless apart from the semaphore: it caps parallel log scans and a request beyond the cap
 * is refused at once instead of queuing, so replay can never starve the request threads. It runs
 * on those threads and never on the serial or UDP ones.</p>
 */
@Service
public class TrackQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(TrackQueryService.class);
    private static final Duration MAX_RANGE = Duration.ofHours(24);

    private final TrackLogSource source;
    private final TracksProperties properties;
    private final Semaphore scans;

    public TrackQueryService(TrackLogSource source, TracksProperties properties) {
        this.source = source;
        this.properties = properties;
        this.scans = new Semaphore(properties.maxConcurrentQueries());
    }

    /**
     * @param from first second wanted, display zone
     * @param to   last second wanted, display zone; the whole second is included
     * @throws InvalidTrackQueryException the request is wrong
     * @throws TrackQueryBusyException    the maximum of concurrent queries is already running
     */
    public TrackQueryResult query(LocalDateTime from, LocalDateTime to, Set<Integer> modelIds) {
        validate(from, to, modelIds);
        if (!scans.tryAcquire()) {
            throw new TrackQueryBusyException();
        }
        try {
            return scan(toLogZone(from), toLogZone(to).plusSeconds(1), new TreeSet<>(modelIds));
        } finally {
            scans.release();
        }
    }

    private void validate(LocalDateTime from, LocalDateTime to, Set<Integer> modelIds) {
        if (modelIds.isEmpty() || modelIds.stream().anyMatch(id -> Models.fromId(id) == null)) {
            throw new InvalidTrackQueryException("Choose at least one known ship.");
        }
        if (!from.isBefore(to)) {
            throw new InvalidTrackQueryException("The start must be before the end.");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidTrackQueryException("The range may not be longer than 24 hours.");
        }
        if (from.isAfter(LocalDateTime.now(properties.displayZone()))) {
            throw new InvalidTrackQueryException("The range lies in the future.");
        }
    }

    private LocalDateTime toLogZone(LocalDateTime displayTime) {
        return displayTime.atZone(properties.displayZone()).withZoneSameInstant(properties.logZone()).toLocalDateTime();
    }

    /** Points with {@code from <= time < until}, log zone. */
    private TrackQueryResult scan(LocalDateTime from, LocalDateTime until, Set<Integer> modelIds) {
        Map<Integer, List<TrackPoint>> points = new TreeMap<>();
        modelIds.forEach(id -> points.put(id, new ArrayList<>()));
        ZoneId logZone = properties.logZone();
        int[] unreadable = {0};
        String[] firstUnreadable = {null};

        var files = source.filesFor(from.toLocalDate(), until.toLocalDate());
        for (var file : files) {
            source.read(file, line -> {
                LocalDateTime time = TrackLogLineParser.timeOf(line);
                if (time == null || time.isBefore(from) || !time.isBefore(until)) {
                    return;
                }
                try {
                    TrackLogLineParser.parse(line)
                            .filter(fix -> modelIds.contains(fix.modelId()))
                            .ifPresent(fix -> points.get(fix.modelId()).add(new TrackPoint(
                                    time.atZone(logZone).toInstant().toEpochMilli(), fix.x(), fix.y(), fix.heading())));
                } catch (IllegalArgumentException e) {
                    if (unreadable[0]++ == 0) {
                        firstUnreadable[0] = line;
                    }
                }
            });
        }
        if (unreadable[0] > 0) {
            LOG.warn("{} lines of the tracking log looked like track lines but could not be read, first: {}",
                    unreadable[0], firstUnreadable[0]);
        }

        List<ShipTrack> ships = new ArrayList<>();
        Long first = null;
        Long last = null;
        for (var entry : points.entrySet()) {
            List<TrackPoint> track = entry.getValue();
            track.sort(Comparator.comparingLong(TrackPoint::time));
            ships.add(new ShipTrack(entry.getKey(), track));
            if (!track.isEmpty()) {
                first = first == null ? track.getFirst().time() : Math.min(first, track.getFirst().time());
                last = last == null ? track.getLast().time() : Math.max(last, track.getLast().time());
            }
        }
        return new TrackQueryResult(ships, coverage(!files.isEmpty(), first, last));
    }

    private TrackCoverage coverage(boolean logsFound, Long firstPoint, Long lastPoint) {
        SortedSet<LocalDate> days = source.availableDays();
        return new TrackCoverage(logsFound,
                days.isEmpty() ? null : days.first(), days.isEmpty() ? null : days.last(),
                firstPoint, lastPoint);
    }
}
