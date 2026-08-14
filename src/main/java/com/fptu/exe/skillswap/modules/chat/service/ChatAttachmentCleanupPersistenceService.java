package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachmentState;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatAttachmentCleanupPersistenceService {

    private final ChatAttachmentRepository attachmentRepository;

    @Transactional
    public int markDeletedIfStillEligible(UUID attachmentId, LocalDateTime cutoff, LocalDateTime now) {
        return attachmentRepository.markDeletedIfStillEligible(
                attachmentId, now, ChatAttachmentState.DELETED,
                EnumSet.of(ChatAttachmentState.EXPIRED, ChatAttachmentState.REVOKED, ChatAttachmentState.TAKEN_DOWN),
                cutoff, now);
    }
}
