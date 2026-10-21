package com.jaworski.serialprotocol.service.ingest;

import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.utils.JsonMapperService;
import com.jaworski.serialprotocol.service.utils.MassageValuesOperations;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import com.jaworski.serialprotocol.service.utils.impl.MessageCommon;
import com.jaworski.serialprotocol.service.utils.impl.MessageLadyMarie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The pipeline behind every transport, exercised with a real translator and real fixtures: the
 * checksummed arrays come from {@code ModelDTOTest}, the unknown-prefix one is the first of them
 * with the prefix swapped and the checksum recomputed.
 */
@ExtendWith(MockitoExtension.class)
class FrameIngestServiceTest {

    private static final String ORIGIN = "Physical Port S0";
    private static final int CHECKSUM_INDEX_27 = 24;

    private static final byte[] WARTA = {119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10};
    private static final byte[] LADY_MARIE = {108, 54, 8, -96, 0, 0, 100, 100, 100, -73, 97, 0, 0, 124, 2, 23, -44, 95, 66, 57, -39, 126, 66, -127, 0, -97, -118, 13, 10};
    private static final byte[] UNKNOWN_PREFIX = {120, 57, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 19, 13, 10};
    private static final byte[] FRAGMENT = {119, 49, 13, 10};

    @Mock
    private WebSocketPublisher publisher;

    private FrameIngestService service;

    @BeforeEach
    void setUp() {
        MassageValuesOperations values = new MassageValuesOperations();
        MessageTranslator translator = new MessageTranslator(new MessageCommon(values), new MessageLadyMarie(values));
        service = new FrameIngestService(publisher, new JsonMapperService(), translator);
    }

    @Test
    void aValidFrameGoesOutRawFirstAndThenAsJson() {
        service.ingest(WARTA, ORIGIN);

        InOrder inOrder = inOrder(publisher);
        inOrder.verify(publisher).publishForAllClients(Arrays.toString(WARTA), SessionType.RS);
        inOrder.verify(publisher).publishForAllClients(argThat((String json) -> json.contains("\"modelName\":1")), eq(SessionType.JSON));
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void aFrameWithABadChecksumGoesOutRawButNeverAsJson() {
        byte[] corrupted = WARTA.clone();
        corrupted[CHECKSUM_INDEX_27]++;

        service.ingest(corrupted, ORIGIN);

        verify(publisher).publishForAllClients(Arrays.toString(corrupted), SessionType.RS);
        verify(publisher, never()).publishForAllClients(anyString(), eq(SessionType.JSON));
    }

    @Test
    void aLadyMarieFrameIsRecognisedByItsLength() {
        service.ingest(LADY_MARIE, ORIGIN);

        verify(publisher).publishForAllClients(argThat((String json) -> json.contains("\"modelName\":6")), eq(SessionType.JSON));
    }

    @Test
    void aFragmentGoesOutRawAndIsSwallowedAfterwards() {
        assertDoesNotThrow(() -> service.ingest(FRAGMENT, ORIGIN));

        verify(publisher).publishForAllClients(Arrays.toString(FRAGMENT), SessionType.RS);
        verify(publisher, never()).publishForAllClients(anyString(), eq(SessionType.JSON));
    }

    @Test
    void twoFramesGluedByALostDelimiterNeverReachJson() {
        // The first frame lost its CR LF, so the listener delivers both as one 52-byte message.
        byte[] glued = new byte[WARTA.length - 2 + WARTA.length];
        System.arraycopy(WARTA, 0, glued, 0, WARTA.length - 2);
        System.arraycopy(WARTA, 0, glued, WARTA.length - 2, WARTA.length);

        service.ingest(glued, ORIGIN);

        verify(publisher).publishForAllClients(Arrays.toString(glued), SessionType.RS);
        verify(publisher, never()).publishForAllClients(anyString(), eq(SessionType.JSON));
    }

    @Test
    void aFailureAfterTheRawPublishIsLoggedAndNeverReachesTheTransportThread() {
        org.mockito.Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(1) == SessionType.JSON) {
                throw new IllegalStateException("socket gone");
            }
            return null;
        }).when(publisher).publishForAllClients(anyString(), org.mockito.ArgumentMatchers.any(SessionType.class));

        assertDoesNotThrow(() -> service.ingest(WARTA, ORIGIN));

        verify(publisher).publishForAllClients(Arrays.toString(WARTA), SessionType.RS);
    }

    @Test
    void anUnknownPrefixWithAValidChecksumStillReachesJson() {
        // Parity with what the serial path always did: there is no ship-id gate, only a checksum gate.
        service.ingest(UNKNOWN_PREFIX, ORIGIN);

        verify(publisher).publishForAllClients(argThat((String json) -> json.contains("\"modelName\":-1")), eq(SessionType.JSON));
    }
}
