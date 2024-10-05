package com.jaworski.serialprotocol.dto;

import lombok.*;

import java.io.Serializable;

@ToString
@Setter
@Getter
public class CheckBoxOption implements Serializable {

    private boolean option1;
    private boolean option2;
    private boolean option3;

}
