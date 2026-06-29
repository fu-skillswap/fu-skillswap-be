package com.fptu.exe.skillswap.modules.academic.repository;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    @EntityGraph(attributePaths = {"user", "campus", "program", "specialization"})
    Optional<StudentProfile> findWithDetailsByUserId(UUID userId);

    List<StudentProfile> findByUserIdIn(List<UUID> userIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StudentProfile profile
            set profile.semester = profile.semester + 1,
                profile.updatedAt = :updatedAt
            where profile.isAlumni = false
              and profile.semester is not null
              and profile.semester > 0
              and profile.semester < 9
            """)
    int incrementEligibleSemesters(@Param("updatedAt") LocalDateTime updatedAt);
}
