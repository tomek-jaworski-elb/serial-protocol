package com.jaworski.serialprotocol.dto.custom;

import java.util.Base64;
import java.util.UUID;

public record ImageDTO(
        UUID uuid,
        byte[] data,
        String contentType
) {
  public ImageDTO(byte[] data, String contentType) {
    this(null, data, contentType);
  }

  public String getImageBase64() {
    return Base64.getEncoder().encodeToString(data);
  }
}
