package com.fptu.exe.skillswap.modules.conversation.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "chat_upload_intents") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatUploadIntent {
    @Id @GeneratedUuidV7 private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id", nullable = false) private Conversation conversation;
    @Column(name = "owner_user_id", nullable = false) private UUID ownerUserId;
    @Column(name = "storage_key", nullable = false, unique = true) private String storageKey;
    @Column(name = "original_filename", nullable = false) private String originalFilename;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "expected_size_bytes", nullable = false) private long expectedSizeBytes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default private ChatUploadIntentStatus status = ChatUploadIntentStatus.PENDING_UPLOAD;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
