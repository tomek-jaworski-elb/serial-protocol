/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for image entity backup. Stores binary data as byte[]; Jackson
 * automatically serializes/deserializes it as Base64.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageBackupDTO {

    private UUID uuid;
    /** Raw image bytes – Jackson encodes/decodes as Base64. */
    private byte[] data;
    private String contentType;
}

