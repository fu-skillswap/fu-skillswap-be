package com.fptu.exe.skillswap.modules.matching.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentoring_questionnaire_activation_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentoringQuestionnaireActivation {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mqa_version"))
    private MentoringQuestionnaireVersion version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activated_by", foreignKey = @ForeignKey(name = "fk_mqa_activated_by"))
    private User activatedBy;

    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @PrePersist
    protected void onCreate() {
        if (activatedAt == null) {
            activatedAt = DateTimeUtil.now();
        }
    }
}
