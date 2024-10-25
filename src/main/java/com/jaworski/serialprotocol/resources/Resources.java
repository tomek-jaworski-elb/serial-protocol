package com.jaworski.serialprotocol.resources;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class Resources {

    private static final Logger LOG = LogManager.getLogger(Resources.class);
    public static final int DEFAULT_BAUD_RATE = 9600;
    private static final String LOGGING_FILE_PATH = "logging.file.path";
    private static final String LOGGING_TRACKING_FILE_NAME = "logging.tracking.file.name";

    @Value("${rs.baud_rate}")
    private String rsBaudRate;

    @Value("${rs.comports}")
    @Getter
    private String comportsName;

    @Value("${rs.message_delimiter}")
    @Getter
    private byte[] messageDelimiter;

    @Getter
    @Value("${ws.endpoint}")
    private String wsEndpoint;

    public Integer getBaudRate() {
        try {
            return Integer.parseInt(rsBaudRate);
        } catch (NumberFormatException e) {
            LOG.error("Could not parse baud rate: {}", rsBaudRate);
            return DEFAULT_BAUD_RATE;
        }
    }

    @Value("${ip.db-client}")
    private String dbClientIp;

    public String getDbClientIp() {
        return StringUtils.isNoneEmpty(dbClientIp) ? dbClientIp : "";
    }

    @Value("${rest.service.enabled}")
    private String restServiceEnabled;

    public boolean isRestServiceEnabled() {
        return Boolean.parseBoolean(restServiceEnabled);
    }

    @Value("${name.service.password}")
    @Getter
    private String nameServicePassword;

    @Value("${rest.service.credentials}")
    @Getter
    private String restServiceCredentials;

    @Value("${" + LOGGING_TRACKING_FILE_NAME + ":#{null}}")
    private Optional<String> trackingLogFileName;

    @Value("${" + LOGGING_FILE_PATH + ":#{null}}")
    private Optional<String> logFilePath;

    public String getLogFilePath() {
        return logFilePath.filter(s -> {
                    if (StringUtils.isNotBlank(s)) {
                        return true;
                    } else {
                        LOG.info("Property + " + LOGGING_FILE_PATH + " is blank for log file path. Set default logs");
                        return false;
                    }
                })
                .orElseGet(() -> {
                    LOG.info("Cant find property + " + LOGGING_FILE_PATH + " for file name. Set default logs");
                    return "logs";
                });
    }

    public String getTrackingLogFileName() {
        return trackingLogFileName.filter(s -> {
                    if (StringUtils.isNotBlank(s)) {
                        return true;
                    } else {
                        LOG.info("Property " + LOGGING_TRACKING_FILE_NAME + " is blank. Set default tracking");
                        return false;
                    }
                })
                .orElseGet(() -> {
                    LOG.info("Cant find property " + LOGGING_TRACKING_FILE_NAME + ". Set default tracking");
                    return "tracking";
                });
    }
}
