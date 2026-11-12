package com.jaworski.serialprotocol.dto.tracks;

import com.fasterxml.jackson.annotation.JsonFormat;

/** One position, serialised as {@code [time, x, y, heading]} to keep half a million of them small. */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public record TrackPoint(long time, float x, float y, double heading) {
}
