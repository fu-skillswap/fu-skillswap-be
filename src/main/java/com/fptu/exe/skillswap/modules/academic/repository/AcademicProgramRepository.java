package com.fptu.exe.skillswap.modules.academic.repository;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcademicProgramRepository extends JpaRepository<AcademicProgram, UUID> {
    Optional<AcademicProgram> findByCode(String code);
    List<AcademicProgram> findByIsActiveTrue();
    Optional<AcademicProgram> findByIdAndIsActiveTrue(UUID id);
    boolean existsByIdAndIsActiveTrue(UUID id);
}
