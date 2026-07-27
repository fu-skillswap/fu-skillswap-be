package com.fptu.exe.skillswap.modules.conversation.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "chat_attachments") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatAttachment {
    @Id @GeneratedUuidV7 private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "message_id", nullable = false) private Message message;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "upload_intent_id", nullable = false, unique = true) private ChatUploadIntent uploadIntent;
    @Column(name = "storage_key", nullable = false, unique = true) private String storageKey;
    @Column(name = "original_filename", nullable = false) private String originalFilename;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default private ChatAttachmentState state = ChatAttachmentState.ACTIVE;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "hold_until") private LocalDateTime holdUntil;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
