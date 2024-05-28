package com.jaworski.serialprotocol.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@ToString
public class ModelTrackDTO {

    private int modelName;
    private float positionX;
    private float positionY;
    private double speed;
    private double heading;
}
