package com.fptu.exe.skillswap.infrastructure.storage.archive;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageArchiveServiceTest {

    @Mock
    private ObjectProvider<StorageGateway> storageGatewayProvider;

    @Mock
    private StorageGateway storageGateway;

    private StorageArchiveService service;

    @BeforeEach
    void setUp() {
        when(storageGatewayProvider.getIfAvailable()).thenReturn(storageGateway);
        service = new StorageArchiveService(storageGatewayProvider);
    }

    @Test
    void caseA_UploadSuccess_VerifySuccess_ReturnsResult() throws IOException {
        // Arrange
        List<String> jsonLines = List.of("{\"id\":1}", "{\"id\":2}");
        
        doAnswer(invocation -> {
            Map<String, String> metadata = invocation.getArgument(3);
            Path uploadedFile = invocation.getArgument(1);
            String sha256 = metadata.get("sha256");
            when(storageGateway.headObject(anyString())).thenReturn(
                new StorageGateway.ObjectMetadata("key", "application/gzip", Files.size(uploadedFile),
                        Map.of("sha256", sha256, "row-count", metadata.get("row-count")))
            );
            return null;
        }).when(storageGateway).uploadFile(anyString(), any(), anyString(), any());

        // Act
        StorageArchiveService.ArchiveBatchResult result = service.archiveJsonLines("archives/telemetry/", 1, 2, jsonLines);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.rowCount());
        assertNotNull(result.sha256Hex());
        verify(storageGateway, times(1)).uploadFile(anyString(), any(), eq("application/gzip"), any());
        verify(storageGateway, times(1)).headObject(anyString());
    }

    @Test
    void caseB_UploadThrowsException_PropagatesException() throws IOException {
        // Arrange
        List<String> jsonLines = List.of("{\"id\":1}");
        doThrow(new RuntimeException("S3 Network Error")).when(storageGateway).uploadFile(anyString(), any(), anyString(), any());

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> 
            service.archiveJsonLines("archives/telemetry/", 1, 1, jsonLines)
        );
        assertEquals("S3 Network Error", ex.getMessage());
        
        verify(storageGateway, times(1)).uploadFile(anyString(), any(), eq("application/gzip"), any());
        verify(storageGateway, never()).headObject(anyString()); // Never reaches verify
    }

    @Test
    void caseC_UploadSuccess_VerifyMismatch_ThrowsException() throws IOException {
        // Arrange
        List<String> jsonLines = List.of("{\"id\":1}");
        
        doAnswer(invocation -> {
            Map<String, String> metadata = invocation.getArgument(3);
            Path uploadedFile = invocation.getArgument(1);
            when(storageGateway.headObject(anyString())).thenReturn(
                new StorageGateway.ObjectMetadata("key", "application/gzip", Files.size(uploadedFile),
                        Map.of("sha256", "wrong-sha-256", "row-count", metadata.get("row-count")))
            );
            return null;
        }).when(storageGateway).uploadFile(anyString(), any(), anyString(), any());

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> 
            service.archiveJsonLines("archives/telemetry/", 1, 1, jsonLines)
        );
        assertTrue(ex.getMessage().contains("Integrity mismatch: expected sha256"));
    }

    // Case D is practically handled by the Scheduler transaction. If delete fails, transaction rolls back, 
    // the row remains, and retry will happen. The deterministic objectKey ensures idempotent upload.
    @Test
    void caseD_DeterministicObjectKey_EnsuresIdempotentRetry() throws IOException {
        // Arrange
        List<String> jsonLines = List.of("{\"id\":1}");
        
        doAnswer(invocation -> {
            Map<String, String> metadata = invocation.getArgument(3);
            Path uploadedFile = invocation.getArgument(1);
            String sha256 = metadata.get("sha256");
            when(storageGateway.headObject(anyString())).thenReturn(
                new StorageGateway.ObjectMetadata("key", "application/gzip", Files.size(uploadedFile),
                        Map.of("sha256", sha256, "row-count", metadata.get("row-count")))
            );
            return null;
        }).when(storageGateway).uploadFile(anyString(), any(), anyString(), any());

        // Act
        StorageArchiveService.ArchiveBatchResult result1 = service.archiveJsonLines("archives/telemetry/", 1, 1, jsonLines);
        StorageArchiveService.ArchiveBatchResult result2 = service.archiveJsonLines("archives/telemetry/", 1, 1, jsonLines);

        // Assert
        assertEquals(result1.objectKey(), result2.objectKey(), "Object keys must be identical for identical batches");
        assertEquals(result1.sha256Hex(), result2.sha256Hex());
    }
}
