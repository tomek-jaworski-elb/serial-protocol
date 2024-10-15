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
public class ModelTrackJSDTO {

    private int modelName;
    private float positionX;
    private float positionY;

}