package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.modules.filestorage.port.VerificationDocumentStoragePort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorVerificationAdminDocumentDownloadTest {

    @Mock UserQueryPort userQueryPort;
    @Mock MentorVerificationRequestRepository requestRepository;
    @Mock MentorVerificationDocumentRepository documentRepository;
    @Mock MentorVerificationRequestEventRepository eventRepository;
    @Mock MentorProfileRepository profileRepository;
    @Mock AcademicService academicService;
    @Mock MentorProfileService mentorProfileService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ObjectProvider<StorageGateway> storageGatewayProvider;
    @Mock StorageGateway storageGateway;
    @Mock StorageProperties storageProperties;
    @Mock VerificationDocumentStoragePort verificationDocumentStoragePort;
    @InjectMocks MentorVerificationAdminPortImpl service;

    @Test
    void getDocumentDownloadUrl_signsOwnedDocumentInlineWithConfiguredTtl() {
        UUID requestId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID storedFileId = UUID.randomUUID();
        Instant expiry = Instant.parse("2026-10-07T10:15:00Z");
        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .id(documentId).storedFileId(storedFileId).originalFilename("proof\".pdf").build();
        when(documentRepository.findByIdAndRequestId(documentId, requestId)).thenReturn(Optional.of(document));
        when(verificationDocumentStoragePort.findVerificationDocumentStorageKey(storedFileId))
                .thenReturn(Optional.of("skillswap/verification-documents/proof.pdf"));
        when(storageProperties.getPresignedTtlMinutes()).thenReturn(15);
        when(storageGatewayProvider.getIfAvailable()).thenReturn(storageGateway);
        when(storageGateway.generatePrivateDownloadUrl(anyString(), any(), anyString()))
                .thenReturn(new StorageGateway.PrivatePresignedDownload("https://storage.example/signed", expiry));

        var result = service.getDocumentDownloadUrl(requestId, documentId);

        assertEquals("https://storage.example/signed", result.downloadUrl());
        assertEquals(expiry, result.expiresAt().toInstant());
        verify(storageGateway).generatePrivateDownloadUrl(eq("skillswap/verification-documents/proof.pdf"),
                eq(java.time.Duration.ofMinutes(15)), eq("inline; filename=\"proof_.pdf\""));
    }

    @Test
    void getDocumentDownloadUrl_rejectsDocumentFromDifferentRequest() {
        UUID requestId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findByIdAndRequestId(documentId, requestId)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () -> service.getDocumentDownloadUrl(requestId, documentId));
        verifyNoInteractions(storageGatewayProvider, storageGateway, verificationDocumentStoragePort);
    }
}
