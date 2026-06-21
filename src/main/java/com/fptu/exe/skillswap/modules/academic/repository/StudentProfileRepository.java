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
}
