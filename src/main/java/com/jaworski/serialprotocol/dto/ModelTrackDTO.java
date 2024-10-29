package com.jaworski.serialprotocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@ToString
@Builder
public class ModelTrackDTO {

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