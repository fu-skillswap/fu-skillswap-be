package com.fptu.exe.skillswap.modules.conversation.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations", indexes = {
    @Index(name = "idx_conversations_source", columnList = "source_type, source_id", unique = true),
    @Index(name = "idx_conversations_last_message_at", columnList = "last_message_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private ConversationSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "lock_at")
    private LocalDateTime lockAt;

    @Column(name = "last_message_content", length = 1000)
    private String lastMessageContent;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
