package com.jaworski.serialprotocol.serial.listener;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.service.ingest.FrameIngress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The serial adapter, driven through a real {@link SerialPortEvent} on a mocked port: this is
 * the serial path without a device, and the only thing it may do is hand the frame on.
 */
@ExtendWith(MockitoExtension.class)
class SerialPortListenerImplTest {

    private static final byte[] FRAME = {119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10};

    @Mock
    private Resources resources;
    @Mock
    private FrameIngress frameIngress;
    @Mock
    private SerialPort serialPort;

    @Test
    void aReceivedMessageIsHandedToTheIngressWithThePortAsOrigin() {
        when(serialPort.getPortDescription()).thenReturn("Physical Port S0");
        SerialPortListenerImpl listener = new SerialPortListenerImpl(resources, frameIngress);

        listener.serialEvent(new SerialPortEvent(serialPort, SerialPort.LISTENING_EVENT_DATA_RECEIVED, FRAME));

        verify(frameIngress).ingest(FRAME, "Physical Port S0");
        verifyNoMoreInteractions(frameIngress);
    }

    @Test
    void framingFollowsTheConfiguredDelimiter() {
        when(resources.getMessageDelimiter()).thenReturn(new byte[]{13, 10});
        SerialPortListenerImpl listener = new SerialPortListenerImpl(resources, frameIngress);

        assertArrayEquals(new byte[]{13, 10}, listener.getMessageDelimiter());
        assertTrue(listener.delimiterIndicatesEndOfMessage());
        assertEquals(SerialPort.LISTENING_EVENT_DATA_RECEIVED, listener.getListeningEvents());
    }
}
