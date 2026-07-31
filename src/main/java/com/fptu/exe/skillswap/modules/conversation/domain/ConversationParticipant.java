package com.fptu.exe.skillswap.modules.conversation.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_participants", indexes = {
    @Index(name = "idx_conv_participants_user", columnList = "user_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_conv_participant", columnNames = {"conversation_id", "user_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {

    // Using composite key logic or just an auto-generated surrogate key for simplicity?
    // The prompt just said: conversation_id, user_id, joined_at, unique(conversation_id, user_id).
    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_conv_participants_conv"))
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_conv_participants_user"))
    private User user;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "last_read_sequence", nullable = false)
    @Builder.Default
    private long lastReadSequence = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_role", nullable = false, length = 32)
    @Builder.Default
    private ConversationParticipantRole participantRole = ConversationParticipantRole.DIRECT_PARTICIPANT;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_state", nullable = false, length = 32)
    @Builder.Default
    private ConversationParticipantAccess accessState = ConversationParticipantAccess.ACTIVE;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = DateTimeUtil.now();
        }
    }
}
