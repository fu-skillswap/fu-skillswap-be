package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.strategy.AttachmentValidationRegistry;
import com.fptu.exe.skillswap.modules.chat.strategy.DocxZipAttachmentValidator;
import com.fptu.exe.skillswap.modules.chat.strategy.JpegAttachmentValidator;
import com.fptu.exe.skillswap.modules.chat.strategy.PdfAttachmentValidator;
import com.fptu.exe.skillswap.modules.chat.strategy.PngAttachmentValidator;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentValidationRegistryTest {

    private AttachmentValidationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AttachmentValidationRegistry(List.of(
                new PngAttachmentValidator(),
                new JpegAttachmentValidator(),
                new PdfAttachmentValidator(),
                new DocxZipAttachmentValidator()
        ));
    }

    @Test
    void normalizeContentType_validTypes() {
        assertEquals("image/png", registry.normalizeContentType("image/png"));
        assertEquals("image/jpeg", registry.normalizeContentType("IMAGE/JPEG"));
        assertEquals("application/pdf", registry.normalizeContentType("application/pdf"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                registry.normalizeContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void normalizeContentType_unsupportedType_throwsException() {
        assertThrows(BaseException.class, () -> registry.normalizeContentType("video/mp4"));
        assertThrows(BaseException.class, () -> registry.normalizeContentType(""));
    }

    @Test
    void validateFilename_matchingTypes() {
        assertDoesNotThrow(() -> registry.validateFilename("photo.PNG", "image/png"));
        assertDoesNotThrow(() -> registry.validateFilename("avatar.jpeg", "image/jpeg"));
        assertDoesNotThrow(() -> registry.validateFilename("cv.pdf", "application/pdf"));
        assertDoesNotThrow(() -> registry.validateFilename("document.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void validateFilename_mismatchedExtension_throwsException() {
        assertThrows(BaseException.class, () -> registry.validateFilename("photo.exe", "image/png"));
        assertThrows(BaseException.class, () -> registry.validateFilename("cv.doc", "application/pdf"));
    }

    @Test
    void inlineCapable_imagesAreTrue_documentsAreFalse() {
        assertTrue(registry.inlineCapable("image/png"));
        assertTrue(registry.inlineCapable("image/jpeg"));
        assertFalse(registry.inlineCapable("application/pdf"));
        assertFalse(registry.inlineCapable("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void extensionFor_returnsCorrectExtensions() {
        assertEquals(".png", registry.extensionFor("image/png"));
        assertEquals(".jpg", registry.extensionFor("image/jpeg"));
        assertEquals(".pdf", registry.extensionFor("application/pdf"));
        assertEquals(".docx", registry.extensionFor("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }
}
