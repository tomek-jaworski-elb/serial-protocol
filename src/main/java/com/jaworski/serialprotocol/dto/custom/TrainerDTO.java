package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Base64;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainerDTO {

  private Long id;
  private String name;
  private String surname;
  private String email;
  private byte[] photo;

  public String getPhotoBase64() {
    if (photo == null || photo.length == 0) {
      return "";
    }
    return Base64.getEncoder().encodeToString(photo);
  }

}

