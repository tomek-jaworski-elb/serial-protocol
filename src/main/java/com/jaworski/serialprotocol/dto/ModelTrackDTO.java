package com.jaworski.serialprotocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@ToString
@Builder
public class ModelTrackDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int modelName;
    private float positionX;
    private float positionY;
    private double speed;
    private double heading;
    private double rudder;
    private double gpsQuality;
    private double engine;
    private double bowThruster;
    private TugDTO bowTug;
    private TugDTO sternTug;
    private boolean isCRCValid;

}