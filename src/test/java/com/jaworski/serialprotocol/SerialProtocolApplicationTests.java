package com.jaworski.serialprotocol;

import com.jaworski.serialprotocol.configuration.JsonWebSocketHandler;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.serial.controller.SerialController;
import com.jaworski.serialprotocol.serial.listener.SerialPortListenerImpl;
import com.jaworski.serialprotocol.service.SerialPortService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SerialProtocolApplicationTests {

    @Autowired
    private SerialPortListenerImpl serialPortListener;
    @Autowired
    private SerialController serialController;
    @Autowired
    private SerialPortService serialPortService;
    @Autowired
    private JsonWebSocketHandler jsonWebSocketHandler;
    @Autowired
    private Resources resources;

    @Test
    void contextLoads() {
        assertNotNull(serialPortListener);
        assertNotNull(serialController);
        assertNotNull(serialPortService);
        assertNotNull(jsonWebSocketHandler);
        assertNotNull(resources);
    }

}
