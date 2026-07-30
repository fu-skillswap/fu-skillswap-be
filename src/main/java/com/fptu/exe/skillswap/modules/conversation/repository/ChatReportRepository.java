package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.ChatReport;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatReportRepository extends JpaRepository<ChatReport, UUID> {
    boolean existsByConversationIdAndReporterUserIdAndStatus(UUID conversationId, UUID reporterUserId, ChatReportStatus status);
    Optional<ChatReport> findById(UUID id);
    Page<ChatReport> findByStatus(ChatReportStatus status, Pageable pageable);
}
