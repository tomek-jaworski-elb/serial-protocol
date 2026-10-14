package com.jaworski.serialprotocol.dto.custom;

/**
 * One row of the administrator's trash view: enough to recognise the record and to address it
 * ({@code key} is the path segment of the restore and purge actions — a uuid, or the id of a course type).
 */
public record DeletedRecordDTO(String key, String title, String details, String deletedAt, String deletedBy) {
}
