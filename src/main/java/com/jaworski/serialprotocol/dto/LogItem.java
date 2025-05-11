package com.jaworski.serialprotocol.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

@Builder
@Getter
@EqualsAndHashCode
@ToString
public class LogItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long timestamp;
    private ModelTrackJSDTO modelTrack;

}
