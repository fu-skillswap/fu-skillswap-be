package com.fptu.exe.skillswap.modules.academic.repository;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    boolean existsByStudentCodeAndUserIdNot(String studentCode, UUID userId);
}
