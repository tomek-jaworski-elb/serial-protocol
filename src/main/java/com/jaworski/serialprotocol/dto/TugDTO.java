package com.jaworski.serialprotocol.dto;

import lombok.*;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@ToString
@Builder
public class TugDTO {

    private double tugForce;
    private double tugDirection;
}
