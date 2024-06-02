package com.jaworski.serialprotocol.dto;

import lombok.*;

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
    private double tugBowForce;
    private double tugBowDirection;
    private double tugSternForce;
    private double tugSternDirection;
}
