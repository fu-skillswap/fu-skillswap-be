package com.fptu.exe.skillswap.modules.academic.repository;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    @EntityGraph(attributePaths = {"user", "campus", "program", "specialization"})
    Optional<StudentProfile> findWithDetailsByUserId(UUID userId);

    List<StudentProfile> findByUserIdIn(List<UUID> userIds);

    @org.springframework.data.jpa.repository.Query("SELECT s.claimedStudentCode FROM StudentProfile s WHERE s.claimedStudentCode IN :claimedCodes GROUP BY s.claimedStudentCode HAVING COUNT(s) > 1")
    java.util.List<String> findConflictingClaimedStudentCodes(@org.springframework.data.repository.query.Param("claimedCodes") java.util.List<String> claimedCodes);
}
