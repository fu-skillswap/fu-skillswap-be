package com.fptu.exe.skillswap.modules.matching.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentoring_questionnaire_answers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentoringQuestionnaireAnswer {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mqans_activation"))
    private MentoringQuestionnaireActivation activation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mqans_version"))
    private MentoringQuestionnaireVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mqans_user"))
    private User user;

    @Column(name = "question_code", nullable = false, length = 80)
    private String questionCode;

    @Column(name = "option_code", nullable = false, length = 100)
    private String optionCode;

    @Column(name = "score_value")
    private Integer scoreValue;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    @PrePersist
    protected void onCreate() {
        if (answeredAt == null) {
            answeredAt = DateTimeUtil.now();
        }
    }
}
