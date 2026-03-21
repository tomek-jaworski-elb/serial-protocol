package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDTO {

  private UUID uuid;
  private Long id;
  private String name;
  private String surname;
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;
  private byte[] photo;

  public String getPhotoBase64() {
    if (photo == null || photo.length == 0) {
      return "";
    }
    return Base64.getEncoder().encodeToString(photo);
  }

}

