package com.fptu.exe.skillswap.modules.matching.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_insight_logs", indexes = {
    @Index(name = "idx_ai_insights_user_id", columnList = "user_id"),
    @Index(name = "idx_ai_insights_source", columnList = "source_type, source_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInsightLog {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_ai_insights_user"))
    private User user;

    @Column(name = "source_type", nullable = false, length = 80)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "insight_type", nullable = false, length = 80)
    private String insightType;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(columnDefinition = "jsonb")
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
