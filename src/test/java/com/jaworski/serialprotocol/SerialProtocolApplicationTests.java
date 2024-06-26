package com.jaworski.serialprotocol;

import com.jaworski.serialprotocol.serial.controller.SerialController;
import com.jaworski.serialprotocol.serial.listener.SerialPortListenerImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class SerialProtocolApplicationTests {

    @Autowired
    private SerialPortListenerImpl serialPortListener;
    @Autowired
    private SerialController serialController;

    @Test
    void contextLoads() {
        assertNotNull(serialPortListener);
        assertNotNull(serialController);
    }

}
