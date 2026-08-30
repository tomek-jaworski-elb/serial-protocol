package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianDTO {

  private UUID id;
  private String name;
  private String surname;
  private String notes;
  private String nickname;
  private String email;
  private String phoneNumber;
  private String address;

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Set<UUID> imagesUuid = new HashSet<>();

  /**
   * The image ids as a plain comma-separated list, for templates to hand to JavaScript.
   *
   * <p>Rendering the Set itself yields its toString form, {@code [a, b]}, which every
   * consumer then had to strip brackets from and split — the same two-line helper ended
   * up copied into three templates and again into the photo editor. Mirrors
   * {@code CoursesDTO.getTrainerIdsString()}.</p>
   */
  public String getImagesUuidString() {
    return imagesUuid == null ? ""
        : imagesUuid.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

}

