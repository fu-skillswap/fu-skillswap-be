package com.fptu.exe.skillswap.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileStorageServiceTest {

    @Test
    void store_whenStorageIsNotReady_shouldThrowClearIllegalState() throws Exception {
        FileStorageService fileStorageService = new FileStorageService();
        setField(fileStorageService, "storageReady", false);

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> fileStorageService.store(file));

        assertEquals(
                "Dịch vụ lưu trữ tệp hiện chưa sẵn sàng. Vui lòng kiểm tra cấu hình application.upload.dir và quyền ghi thư mục.",
                ex.getMessage()
        );
    }

    @Test
    void delete_whenStorageIsNotReady_shouldNotThrow() throws Exception {
        FileStorageService fileStorageService = new FileStorageService();
        setField(fileStorageService, "storageReady", false);

        assertDoesNotThrow(() -> fileStorageService.delete("uploads/files/test.txt"));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = FileStorageService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
