package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.dto.LogItem;
import com.jaworski.serialprotocol.dto.TugDTO;
import com.jaworski.serialprotocol.resources.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {TrackService.class, ReadLogsFileService.class, Resources.class})
class TrackServiceTest {

    @Mock
    private ReadLogsFileService readLogsFileService;

    @InjectMocks
    private TrackService trackService;

    @BeforeAll
    static void setup() {
        MockitoAnnotations.openMocks(TrackServiceTest.class);//without this you will get NPE
    }

    @Test
    void returnEmptyMap_whenNoModels() throws IOException {
        when(readLogsFileService.readLogs()).thenReturn(List.of("19-09-2024 17:24:45.570 - Translated message: ModelTrackDTO(modelName=6, positionX=-155.96, positionY=63.71, speed=0.0, heading=229.8, rudder=0.0, gpsQuality=1.59, engine=100.0, bowThruster=-127.0, bowTug=TugDTO(tugForce=10.0, tugDirection=0.0), sternTug=TugDTO(tugForce=10.0, tugDirection=469.45))\n"));
        Map<Integer, List<LogItem>> models = trackService.getModels(Collections.emptySet());
        assertNotNull(models);
        assertTrue(models.isEmpty());
    }

    @Test
    void returnEmptyMap_whenReadEmptyLogs_whenNoModels() throws IOException {
        when(readLogsFileService.readLogs()).thenReturn(Collections.emptyList());
        Map<Integer, List<LogItem>> models = trackService.getModels(Collections.emptySet());
        assertNotNull(models);
        assertTrue(models.isEmpty());
    }

    @Test
    void returnEmptyMap_whenReadEmptyLogs_whenModel6() throws IOException {
        when(readLogsFileService.readLogs()).thenReturn(Collections.emptyList());
        Map<Integer, List<LogItem>> models = trackService.getModels(Set.of(6));
        assertNotNull(models);
        assertTrue(models.isEmpty());
    }

    @Test
    void returnEmptyMap_whenModel5() throws IOException {
        when(readLogsFileService.readLogs()).thenReturn(List.of("19-09-2024 17:24:45.570 - Translated message: ModelTrackDTO(modelName=6, positionX=-155.96, positionY=63.71, speed=0.0, heading=229.8, rudder=0.0, gpsQuality=1.59, engine=100.0, bowThruster=-127.0, bowTug=TugDTO(tugForce=10.0, tugDirection=0.0), sternTug=TugDTO(tugForce=10.0, tugDirection=469.45))\n"));
        Map<Integer, List<LogItem>> models = trackService.getModels(Set.of(6, 5));
        assertNotNull(models);
        assertTrue(models.containsKey(6));
        assertFalse(models.containsKey(5));
        assertEquals(1, models.get(6).size());
    }

    @Test
    void readModels_whenCollectionOf2() throws IOException {
        List<String> collection = new ArrayList<>();
        collection.add("21-09-2024 18:43:58.695 - Translated message: ModelTrackDTO(modelName=3, positionX=30.85, positionY=80.1, speed=0.1, heading=137.1, rudder=10.5, gpsQuality=0.09, engine=101.0, bowThruster=-128.0, bowTug=TugDTO(tugForce=9.7, tugDirection=7.72), sternTug=TugDTO(tugForce=9.7, tugDirection=10.2))\n");
        collection.add("21-09-2024 18:50:13.723 - Translated message: ModelTrackDTO(modelName=6, positionX=55.96, positionY=-63.71, speed=0.0, heading=229.8, rudder=0.0, gpsQuality=1.59, engine=100.0, bowThruster=-127.0, bowTug=TugDTO(tugForce=10.0, tugDirection=0.0), sternTug=TugDTO(tugForce=10.0, tugDirection=469.45))\n");
        when(readLogsFileService.readLogs()).thenReturn(collection);
        var modelTrackDTOS = trackService.getModels(Set.of(3, 6));
        assertNotNull(modelTrackDTOS);
        assertFalse(modelTrackDTOS.isEmpty());
        assertEquals(2, modelTrackDTOS.size());
        assertFalse(modelTrackDTOS.containsKey(0));
        assertTrue(modelTrackDTOS.containsKey(3));
        assertTrue(modelTrackDTOS.containsKey(3));
        modelTrackDTOS.get(3).forEach(logItem -> assertEquals(3, logItem.getModelTrack().getModelName()));
        modelTrackDTOS.get(6).forEach(logItem -> assertEquals(6, logItem.getModelTrack().getModelName()));
        assertEquals(55.96, modelTrackDTOS.get(6).get(0).getModelTrack().getPositionX(), 0.01);
        assertEquals(-63.71, modelTrackDTOS.get(6).get(0).getModelTrack().getPositionY(), 0.01);
    }

    @Test
    void readModel2() throws IOException {
        when(readLogsFileService.readLogs()).thenReturn(List.of("21-09-2024 18:43:58.682 - Translated message: ModelTrackDTO(modelName=2, positionX=-29.39, positionY=-76.08, speed=0.2, heading=320.9, rudder=-12.6, gpsQuality=0.04, engine=100.0, bowThruster=127.0, bowTug=TugDTO(tugForce=0.3, tugDirection=31.19), sternTug=TugDTO(tugForce=0.3, tugDirection=31.19))\n"));
        List<LogItem> modelTrackDTOS = trackService.getModels(Set.of(2, 6)).get(2);
        assertNotNull(modelTrackDTOS);
        assertFalse(modelTrackDTOS.isEmpty());
        assertEquals(1, modelTrackDTOS.size());
        LogItem modelTrackDTO = modelTrackDTOS.get(0);
        assertNotNull(modelTrackDTO);
        assertEquals(2, modelTrackDTO.getModelTrack().getModelName());
        assertEquals(-29.39, modelTrackDTO.getModelTrack().getPositionX(), 0.01);
        assertEquals(-76.08, modelTrackDTO.getModelTrack().getPositionY(), 0.01);
        assertEquals(0.2, modelTrackDTO.getModelTrack().getSpeed(), 0.01);
        assertEquals(320.9, modelTrackDTO.getModelTrack().getHeading(), 0.01);
        assertEquals(-12.6, modelTrackDTO.getModelTrack().getRudder());
        assertEquals(0.04, modelTrackDTO.getModelTrack().getGpsQuality());
        assertEquals(100.0, modelTrackDTO.getModelTrack().getEngine());
        assertEquals(127.0, modelTrackDTO.getModelTrack().getBowThruster());
        TugDTO bowTug = modelTrackDTO.getModelTrack().getBowTug();
        assertNotNull(bowTug);
    }

    @Test
    void readModel6() throws IOException {
        when(readLogsFileService.readLogs()).thenReturn(List.of("19-09-2024 17:24:45.570 - Translated message: ModelTrackDTO(modelName=6, positionX=-155.96, positionY=63.71, speed=0.0, heading=229.8, rudder=0.0, gpsQuality=1.59, engine=100.0, bowThruster=-127.0, bowTug=TugDTO(tugForce=10.0, tugDirection=0.0), sternTug=TugDTO(tugForce=10.0, tugDirection=469.45))\n"));
        List<LogItem> modelTrackDTOS = trackService.getModels(Set.of(2, 6)).get(6);
        assertNotNull(modelTrackDTOS);
        assertFalse(modelTrackDTOS.isEmpty());
        assertEquals(1, modelTrackDTOS.size());
        LogItem modelTrackDTO = modelTrackDTOS.get(0);
        assertNotNull(modelTrackDTO);
        assertEquals(6, modelTrackDTO.getModelTrack().getModelName());
        assertEquals(-155.96, modelTrackDTO.getModelTrack().getPositionX(), 0.01);
        assertEquals(63.71, modelTrackDTO.getModelTrack().getPositionY(), 0.01);
        assertEquals(0.0, modelTrackDTO.getModelTrack().getSpeed(), 0.01);
        assertEquals(229.8, modelTrackDTO.getModelTrack().getHeading(), 0.01);
        assertEquals(0.0, modelTrackDTO.getModelTrack().getRudder());
        assertEquals(1.59, modelTrackDTO.getModelTrack().getGpsQuality());
        assertEquals(100.0, modelTrackDTO.getModelTrack().getEngine());
        assertEquals(-127.0, modelTrackDTO.getModelTrack().getBowThruster());
        TugDTO bowTug = modelTrackDTO.getModelTrack().getBowTug();
        assertNotNull(bowTug);
        assertEquals(10.0, bowTug.getTugForce());
        assertEquals(0.0, bowTug.getTugDirection());
        TugDTO sternTug = modelTrackDTO.getModelTrack().getSternTug();
        assertNotNull(sternTug);
        assertEquals(10.0, sternTug.getTugForce());
        assertEquals(469.45, sternTug.getTugDirection());
    }

}