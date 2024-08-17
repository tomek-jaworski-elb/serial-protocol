package com.jaworski.serialprotocol.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Personel {

    private int id;
    private String name;
    private String lastName;

}
