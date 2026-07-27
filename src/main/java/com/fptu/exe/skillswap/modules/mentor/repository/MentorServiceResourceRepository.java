package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.jpa.repository.Query;

public interface MentorServiceResourceRepository extends JpaRepository<MentorServiceResource, UUID> {
    List<MentorServiceResource> findByServiceIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID serviceId);
    List<MentorServiceResource> findByServiceIdOrderByCreatedAtAsc(UUID serviceId);
    Optional<MentorServiceResource> findByIdAndServiceMentorProfileUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    long countByServiceIdAndDeletedAtIsNull(UUID serviceId);
    @Query("select coalesce(sum(r.sizeBytes), 0) from MentorServiceResource r where r.service.mentorProfile.userId=:mentorId and r.deletedAt is null")
    long sumActiveSizeByMentorId(UUID mentorId);
}
