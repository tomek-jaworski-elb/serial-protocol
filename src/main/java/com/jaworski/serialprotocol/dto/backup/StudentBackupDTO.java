/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Backup DTO for the legacy Student entity.
 * Photo is stored as byte[]; Jackson serializes/deserializes it as Base64.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentBackupDTO {

    private int id;
    private String name;
    private String lastName;
    private String courseNo;
    private Date dateBegine;
    private Date dateEnd;
    private String mrMs;
    private String certType;
    /** Raw photo bytes – Jackson encodes/decodes as Base64. */
    private byte[] photo;
}

