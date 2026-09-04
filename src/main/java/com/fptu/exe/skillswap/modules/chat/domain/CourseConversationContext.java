package com.fptu.exe.skillswap.modules.chat.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Maps one enrolled mentee's course context to one existing chat conversation. */
@Entity
@Table(name = "course_conversation_contexts", indexes = {
        @Index(name = "idx_course_conversation_context_course", columnList = "course_id"),
        @Index(name = "idx_course_conversation_context_mentee", columnList = "mentee_user_id"),
        @Index(name = "idx_course_conversation_context_conversation", columnList = "conversation_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_course_conversation_context_course_mentee", columnNames = {"course_id", "mentee_user_id"}),
        @UniqueConstraint(name = "uq_course_conversation_context_conversation", columnNames = "conversation_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseConversationContext {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "mentee_user_id", nullable = false)
    private UUID menteeUserId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimeUtil.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
