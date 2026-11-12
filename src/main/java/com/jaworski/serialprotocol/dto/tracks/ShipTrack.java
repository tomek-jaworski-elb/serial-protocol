package com.jaworski.serialprotocol.dto.tracks;

import java.util.List;

public record ShipTrack(int id, List<TrackPoint> points) {
}
