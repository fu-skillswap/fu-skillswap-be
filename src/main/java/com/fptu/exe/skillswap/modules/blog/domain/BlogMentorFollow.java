package com.fptu.exe.skillswap.modules.blog.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blog_mentor_follows", uniqueConstraints = @UniqueConstraint(
        name = "uk_blog_mentor_follows_user_mentor", columnNames = {"user_id", "mentor_user_id"}), indexes = {
        @Index(name = "idx_blog_mentor_follows_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_blog_mentor_follows_mentor_user", columnList = "mentor_user_id, user_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogMentorFollow {
    @Id @GeneratedUuidV7 private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    /** Mentor identity is represented by the owning user id, not a mentor aggregate association. */
    @Column(name = "mentor_user_id", nullable = false) private UUID mentorUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = DateTimeUtil.now(); }
}
