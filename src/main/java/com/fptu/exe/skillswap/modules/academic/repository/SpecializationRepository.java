package com.fptu.exe.skillswap.modules.academic.repository;

import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpecializationRepository extends JpaRepository<Specialization, UUID> {
    Optional<Specialization> findByCode(String code);
    List<Specialization> findByIsActiveTrue();
    List<Specialization> findByProgramIdAndIsActiveTrue(UUID programId);
    Optional<Specialization> findByIdAndIsActiveTrue(UUID id);
}
