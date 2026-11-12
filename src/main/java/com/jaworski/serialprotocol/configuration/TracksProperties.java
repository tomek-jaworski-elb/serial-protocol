package com.jaworski.serialprotocol.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * Settings of the track replay ({@code /tracks}).
 *
 * @param logZone              zone the tracking log timestamps were written in (the JVM's zone of
 *                             that time); the system zone when unset. Docker runs in UTC, see
 *                             {@code TRACKS_LOG_ZONE} in docker-compose.yml.
 * @param displayZone          the only zone the user sees: pickers, labels, PDF
 * @param gapSeconds           a line breaks on a gap longer than this; the page may override it
 * @param maxConcurrentQueries log scans allowed at once, the next request gets 503
 */
@ConfigurationProperties(prefix = "tracks")
@Validated
public record TracksProperties(
        ZoneId logZone,
        @DefaultValue("Europe/Warsaw") ZoneId displayZone,
        @DefaultValue("10") @Min(1) @Max(600) int gapSeconds,
        @DefaultValue("2") @Min(1) int maxConcurrentQueries) {

    public TracksProperties {
        if (logZone == null) {
            logZone = ZoneId.systemDefault();
        }
    }
}
