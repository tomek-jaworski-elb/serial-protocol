package com.jaworski.serialprotocol.dto.tracks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * What the tracks page posts to {@code /tracks/pdf}.
 *
 * @param image       the rendered map area, JPEG
 * @param from        start of the range, display zone
 * @param to          end of the range, display zone
 * @param silhouettes period in seconds, 0 for every point, -1 when hidden
 * @param ships       ids of the ships drawn on the image
 * @param widthNm width of the imaged area in full-scale nautical miles of the 1:24 chart, for the scale bar
 * @param captains    optional, ship id to captain name; the names are printed and never kept
 */
public record TrackPdfRequest(byte[] image, LocalDateTime from, LocalDateTime to, int silhouettes,
                              List<Integer> ships, double widthNm,
                              Map<Integer, String> captains) {
}
