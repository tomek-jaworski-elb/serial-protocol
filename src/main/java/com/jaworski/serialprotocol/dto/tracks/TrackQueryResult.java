package com.jaworski.serialprotocol.dto.tracks;

import java.util.List;

public record TrackQueryResult(List<ShipTrack> ships, TrackCoverage coverage) {
}
