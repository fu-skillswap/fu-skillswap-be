package com.fptu.exe.skillswap.modules.matching.repository;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentoringQuestionnaireVersionRepository extends JpaRepository<MentoringQuestionnaireVersion, UUID> {

    Optional<MentoringQuestionnaireVersion> findFirstByActiveTrueOrderByVersionNumberDesc();

    Optional<MentoringQuestionnaireVersion> findFirstByOrderByVersionNumberDesc();

    List<MentoringQuestionnaireVersion> findAllByOrderByVersionNumberDesc();

    @Query("select coalesce(max(v.versionNumber), 0) from MentoringQuestionnaireVersion v")
    int maxVersionNumber();
}
