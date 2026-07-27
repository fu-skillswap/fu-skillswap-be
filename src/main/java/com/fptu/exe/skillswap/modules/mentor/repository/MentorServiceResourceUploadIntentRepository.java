package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceUploadIntent;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface MentorServiceResourceUploadIntentRepository extends JpaRepository<MentorServiceResourceUploadIntent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MentorServiceResourceUploadIntent i join fetch i.service where i.id=:id")
    Optional<MentorServiceResourceUploadIntent> findByIdForUpdate(UUID id);
    List<MentorServiceResourceUploadIntent> findByStatusAndExpiresAtBefore(MentorServiceResourceUploadIntent.Status status, LocalDateTime cutoff);

    @Query(value = """
        select * from mentor_service_resource_upload_intents
        where (status = 'PENDING_UPLOAD' and expires_at < :now)
           or (status = 'EXPIRED' and storage_deleted_at is null and (next_cleanup_at is null or next_cleanup_at <= :now)
               and (cleanup_lease_until is null or cleanup_lease_until < :now))
        order by expires_at asc limit :limit for update skip locked
        """, nativeQuery = true)
    List<MentorServiceResourceUploadIntent> claimCleanupBatch(@org.springframework.data.repository.query.Param("now") LocalDateTime now,
                                                               @org.springframework.data.repository.query.Param("limit") int limit);
}
