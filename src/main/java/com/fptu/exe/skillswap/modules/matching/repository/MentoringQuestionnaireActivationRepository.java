package com.fptu.exe.skillswap.modules.matching.repository;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireActivation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentoringQuestionnaireActivationRepository extends JpaRepository<MentoringQuestionnaireActivation, UUID> {

    Optional<MentoringQuestionnaireActivation> findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MentoringQuestionnaireActivation a where a.deactivatedAt is null")
    Optional<MentoringQuestionnaireActivation> findActiveForUpdate();
}
