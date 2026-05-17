/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Backup DTO for the legacy Instructor entity.
 * All photo fields are stored as byte[]; Jackson serializes/deserializes them as Base64.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstructorBackupDTO {

    private int no;
    private String name;
    private String surname;
    private String email;
    private String phone;
    private String mobile;
    private String city;
    private String address;
    private String postcode;
    private byte[] photo1;
    private byte[] photo2;
    private byte[] photo3;
    private byte[] photo4;
    private String notes;
    private String otherNotes;
    private String certNo;
    private String specialization;
    private String diploma;
    private LocalDate birthDate;
    private String birthPlace;
    private String mrMs;
    private String nick;
    private String noCertificate;
    private LocalDate expirationDate;
}

