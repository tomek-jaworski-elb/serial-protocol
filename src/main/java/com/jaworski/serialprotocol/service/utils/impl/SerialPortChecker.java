package com.jaworski.serialprotocol.service.utils.impl;

import com.fazecast.jSerialComm.SerialPort;
import com.jaworski.serialprotocol.service.SerialPortService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class SerialPortChecker {

  private static final Logger LOG = LogManager.getLogger(SerialPortChecker.class);
  private final SerialPortService serialController;

  public void isPortAvailable(SerialPort serialPort) {
    int lastErrorCode = serialPort.getLastErrorCode();
    if (lastErrorCode != 0) {
      LOG.error("Error code: {}, error location: {}", lastErrorCode, serialPort.getLastErrorLocation());
      serialPort.closePort();
      serialPort.openPort();
    }
  }

  @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
  public void checkPorts() {
    if (serialController.getSerialPorts().isEmpty()) {
      return;
    }
    serialController.getSerialPorts().forEach(this::isPortAvailable);
  }
}
