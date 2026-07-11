package com.fptu.exe.skillswap.modules.matching.repository;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MentoringQuestionnaireQuestionRepository extends JpaRepository<MentoringQuestionnaireQuestion, UUID> {

    List<MentoringQuestionnaireQuestion> findByVersionIdOrderByDisplayOrderAsc(UUID versionId);
}
