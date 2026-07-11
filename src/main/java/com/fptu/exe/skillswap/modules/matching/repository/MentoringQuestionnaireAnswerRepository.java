package com.fptu.exe.skillswap.modules.matching.repository;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MentoringQuestionnaireAnswerRepository extends JpaRepository<MentoringQuestionnaireAnswer, UUID> {

    List<MentoringQuestionnaireAnswer> findByActivationIdAndUserId(UUID activationId, UUID userId);

    List<MentoringQuestionnaireAnswer> findFirst5ByUserIdOrderByAnsweredAtDesc(UUID userId);

    void deleteByActivationIdAndUserId(UUID activationId, UUID userId);

    long countByActivationIdAndUserId(UUID activationId, UUID userId);
}
