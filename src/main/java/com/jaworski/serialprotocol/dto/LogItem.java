package com.jaworski.serialprotocol.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@EqualsAndHashCode
@ToString
public class LogItem {

    private long timestamp;
    private ModelTrackDTO modelTrack;

}
