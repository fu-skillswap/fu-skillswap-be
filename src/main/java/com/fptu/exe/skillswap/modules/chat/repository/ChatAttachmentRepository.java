package com.fptu.exe.skillswap.modules.chat.repository;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {
 List<ChatAttachment> findByMessageId(UUID messageId);
 @org.springframework.data.jpa.repository.Query("select coalesce(sum(a.sizeBytes),0) from ChatAttachment a where a.message.sender.id=:userId and a.createdAt>=:since")
 long sumUploadedBytesByUserSince(@org.springframework.data.repository.query.Param("userId") UUID userId, @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
