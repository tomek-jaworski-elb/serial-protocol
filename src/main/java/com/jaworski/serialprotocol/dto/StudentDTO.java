package com.jaworski.serialprotocol.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class StudentDTO {

    private int id;
    private String name;
    private String lastName;
    private String courseNo;
    private Date dateBegine;
    private Date dateEnd;
    private String mrMs;
    private String certType;
    private String photo;

}
