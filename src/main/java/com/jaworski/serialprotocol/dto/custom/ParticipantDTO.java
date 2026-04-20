package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDTO {

  private UUID participantUuid;
  private Long id;
  private String name;
  private String surname;
  private String notes;
  private String nickname;
  private String email;
  private String phoneNumber;
  private String address;
  @DateTimeFormat(pattern = "dd/MM/yyyy")
  private LocalDate birthDate;

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private UUID image;

}

