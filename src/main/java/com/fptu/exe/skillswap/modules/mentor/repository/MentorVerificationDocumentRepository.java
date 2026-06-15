package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorVerificationDocumentRepository extends JpaRepository<MentorVerificationDocument, UUID> {

    @EntityGraph(attributePaths = {"storedFile", "uploadedBy"})
    List<MentorVerificationDocument> findByRequestIdOrderByUploadedAtAsc(UUID requestId);

    @EntityGraph(attributePaths = {"storedFile", "uploadedBy"})
    List<MentorVerificationDocument> findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(
            UUID requestId,
            VerificationDocumentType documentType
    );

    @EntityGraph(attributePaths = {"storedFile", "uploadedBy"})
    Optional<MentorVerificationDocument> findByIdAndRequestId(UUID id, UUID requestId);

    long countByRequestIdAndDocumentTypeAndIsActiveTrue(UUID requestId, VerificationDocumentType documentType);

    List<MentorVerificationDocument> findByRequestIdAndDocumentTypeInAndIsActiveTrue(UUID requestId, java.util.Collection<VerificationDocumentType> types);
}
