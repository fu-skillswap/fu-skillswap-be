package com.fptu.exe.skillswap.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"test", "local"})
@AutoConfigureMockMvc
@TestPropertySource(properties = "application.upload.dir=./uploads/storage")
class StorageGatewayIntegrationTest {

    @Autowired
    private StorageGateway storageGateway;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenUploadFile_thenFileIsStoredLocallyAndCanBeAccessed() throws Exception {
        // Given
        byte[] content = "Hello SkillSwap Integration Test!".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-integration.txt",
                "text/plain",
                content
        );

        // When
        StorageGateway.StorageUploadResult result = storageGateway.uploadFile(file, "test-subfolder");

        // Then
        assertNotNull(result);
        assertNotNull(result.objectKey());
        assertNotNull(result.publicUrl());
        assertTrue(result.objectKey().contains("test-subfolder"));

        // Verify the file exists on local disk under uploads/storage
        Path filePath = Paths.get("./uploads/storage").resolve(result.objectKey()).toAbsolutePath().normalize();
        assertTrue(Files.exists(filePath), "File should be created on disk");
        assertArrayEquals(content, Files.readAllBytes(filePath));

        // Verify the file is accessible via HTTP GET at /uploads/storage/**
        String pathInfo = result.publicUrl().substring(result.publicUrl().indexOf("/uploads/storage/"));
        mockMvc.perform(get(pathInfo))
                .andExpect(status().isOk())
                .andDo(mvcResult -> {
                    byte[] responseContent = mvcResult.getResponse().getContentAsByteArray();
                    assertArrayEquals(content, responseContent);
                });

        // Clean up
        storageGateway.deleteFile(result.objectKey());
        assertFalse(Files.exists(filePath), "File should be deleted from disk");
    }

    @Test
    void whenGeneratePresignedUrl_thenSimulatedPresignedUrlIsReturned() {
        // When
        StorageGateway.PresignedUpload presigned = storageGateway.generatePresignedUploadUrl(
                "avatar.png",
                "image/png"
        );

        // Then
        assertNotNull(presigned);
        assertNotNull(presigned.uploadUrl());
        assertNotNull(presigned.publicUrl());
        assertNotNull(presigned.objectKey());
        assertTrue(presigned.uploadUrl().contains("/api/files/local-upload?objectKey="));
        assertTrue(presigned.objectKey().endsWith(".png"));
    }
}
