package com.jaworski.serialprotocol.dto;


import lombok.*;

@Setter
@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class SerialPortDTO {

    private String name;
    private int baudRate;
    private int parity;

}
