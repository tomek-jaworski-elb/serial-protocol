package com.jaworski.serialprotocol.service.ingest;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.utils.JsonMapperService;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.util.Arrays;

/**
 * The one pipeline behind every transport, see {@link FrameIngress}.
 *
 * <p>Order matters and is deliberate: the raw bytes go out on {@code /rs} before anything is
 * decided about them, so the terminal page shows exactly what arrived, including frames that are
 * then rejected. Only a frame whose checksum verifies reaches {@code /json}. The
 * "Translated message" line that feeds the tracking file is written by
 * {@link MessageTranslator#getDTO} itself, so every origin feeds {@code /tracks} without this
 * class knowing about it.</p>
 *
 * <p>Nothing escapes this method: a frame of an unsupported length is a fact of life on a serial
 * line and on an open UDP port alike, so it is logged at WARN without a stack trace (the
 * translator's message already carries the bytes); anything else is logged at ERROR with the
 * frame and does not reach the transport's thread.</p>
 */
@RequiredArgsConstructor
@Service
public class FrameIngestService implements FrameIngress {

    private static final Logger LOG = LoggerFactory.getLogger(FrameIngestService.class);

    private final WebSocketPublisher webSocketPublisher;
    private final JsonMapperService jsonMapperService;
    private final MessageTranslator messageTranslator;

    @Override
    public void ingest(byte[] frame, String origin) {
        String raw = Arrays.toString(frame);
        webSocketPublisher.publishForAllClients(raw, SessionType.RS);
        try {
            ModelTrackDTO dto = messageTranslator.getDTO(frame);
            if (!dto.isCRCValid()) {
                LOG.info("Failed to translate message from {}: {}", origin, raw);
                return;
            }
            webSocketPublisher.publishForAllClients(jsonMapperService.toJsonString(dto), SessionType.JSON);
        } catch (IllegalArgumentException e) {
            LOG.warn("Failed to translate message from {}: {}", origin, e.getMessage());
        } catch (JacksonException e) {
            LOG.error("Failed to serialize message from {}: {}", origin, raw, e);
        } catch (RuntimeException e) {
            LOG.error("Failed to process message from {}: {}", origin, raw, e);
        }
    }
}
