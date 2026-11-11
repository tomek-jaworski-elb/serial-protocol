package com.jaworski.serialprotocol.dto.tracks;

import java.time.LocalDate;

/**
 * What the logs held for a query, so the page can say why a result is empty.
 *
 * @param logsFound   a log file covers the queried range
 * @param historyFrom earliest recorded day in the display zone, null when there are no logs at all
 * @param historyTo   latest recorded day, likewise
 * @param firstPoint  epoch millis of the earliest point found for any ship, null when none
 * @param lastPoint   epoch millis of the latest one
 */
public record TrackCoverage(boolean logsFound, LocalDate historyFrom, LocalDate historyTo,
                            Long firstPoint, Long lastPoint) {
}
