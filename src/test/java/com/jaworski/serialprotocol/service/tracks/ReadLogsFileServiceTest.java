package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.resources.Resources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadLogsFileServiceTest {

    @InjectMocks
    private ReadLogsFileService readLogsFileService;

    @Mock
    private Resources resources;

    @Test
    void testReadLogs_FileDoesNotExists() throws IOException {
        try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
            when(resources.getLogFilePath()).thenReturn("logss");
            when(resources.getTrackingLogFileName()).thenReturn("tracking.log");
            filesMockedStatic.when(() -> Files.exists(any())).thenReturn(false);
            filesMockedStatic.when(() -> Files.readAllLines(any())).thenReturn(Collections.emptyList());
            List<String> strings = readLogsFileService.readLogs();

            // Verify that the file was read
            assertThat(strings).isNotNull().asList().isEmpty();
        }
    }

    @Test
    void testReadLogs_FileExists() throws IOException {
        try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
            when(resources.getLogFilePath()).thenReturn("logs");
            when(resources.getTrackingLogFileName()).thenReturn("tracking.log");
            filesMockedStatic.when(() -> Files.exists(any())).thenReturn(true);
            filesMockedStatic.when(() -> Files.readAllLines(any())).thenReturn(List.of("Line 1", "Line 2", "Line 3"));
            List<String> strings = readLogsFileService.readLogs();

            // Verify that the file was read
            assertThat(strings).isNotNull().asList().isNotEmpty().hasSize(3)
                    .element(0).isEqualTo("Line 1");
        }
    }
}