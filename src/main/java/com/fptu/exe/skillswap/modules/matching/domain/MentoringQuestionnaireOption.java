package com.fptu.exe.skillswap.modules.matching.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentoring_questionnaire_options")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentoringQuestionnaireOption {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mqo_question"))
    private MentoringQuestionnaireQuestion question;

    @Column(name = "option_code", nullable = false, length = 100)
    private String optionCode;

    @Column(name = "option_label", nullable = false, columnDefinition = "TEXT")
    private String optionLabel;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "score_value")
    private Integer scoreValue;

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
