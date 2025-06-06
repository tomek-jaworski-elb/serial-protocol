package com.jaworski.serialprotocol.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
public class InstructorDto {

  private int no;
  private String name;
  private String surname;
  private String email;
  private String phone;
  private String mobile;
  private String city;
  private String address;
  private String postcode;
  private String photo1;
  private String photo2;
  private String photo3;
  private String photo4;
  private String notes;
  private String otherNotes;
  private String certNo;
  private String specialization;
  private String diploma;
  private Date birthDate;
  private String birthPlace;
  private String mrMs;
  private String nick;
  private String noCertificate;
  private Date expirationDate;

}
