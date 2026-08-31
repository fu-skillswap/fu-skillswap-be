package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationUploadIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MentorVerificationUploadIntentRepository extends JpaRepository<MentorVerificationUploadIntent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MentorVerificationUploadIntent i where i.id = :id")
    Optional<MentorVerificationUploadIntent> findByIdForUpdate(@Param("id") UUID id);
}
