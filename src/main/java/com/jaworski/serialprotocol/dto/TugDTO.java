package com.jaworski.serialprotocol.dto;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@ToString
@Builder
public class TugDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private double tugForce;
    private double tugDirection;
}
