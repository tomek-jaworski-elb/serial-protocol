package com.jaworski.serialprotocol.service.utils.impl;

import com.fazecast.jSerialComm.SerialPort;
import com.jaworski.serialprotocol.serial.controller.SerialController;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class SerialPortChecker {

  private static final Logger LOG = LogManager.getLogger(SerialPortChecker.class);
  private final SerialController serialController;

  public void isPortAvailable(SerialPort serialPort) {
    int lastErrorCode = serialPort.getLastErrorCode();
    if (lastErrorCode != 0) {
      LOG.error("Error code: {}", lastErrorCode);
      serialPort.closePort();
      serialPort.openPort();
    }
  }

  @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
  public void checkPorts() {
    LOG.info("Checking all open ports...");
    serialController.getAllPorts().forEach(this::isPortAvailable);
  }
}
