package com.fptu.exe.skillswap.modules.matching.repository;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MentoringQuestionnaireOptionRepository extends JpaRepository<MentoringQuestionnaireOption, UUID> {

    List<MentoringQuestionnaireOption> findByQuestionIdInOrderByQuestionDisplayOrderAscDisplayOrderAsc(Collection<UUID> questionIds);
}
