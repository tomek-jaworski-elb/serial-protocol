package com.jaworski.serialprotocol.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class ModelTrackDTO {
    private String modelName;
    private float positionX;
    private float positionY;
}