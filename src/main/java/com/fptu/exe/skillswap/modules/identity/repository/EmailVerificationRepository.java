package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.EmailVerification;
import com.fptu.exe.skillswap.modules.identity.domain.EmailVerificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(String email);

    Optional<EmailVerification> findTopByEmailAndTypeAndVerifiedAtIsNullOrderByCreatedAtDesc(String email, EmailVerificationType type);
}
