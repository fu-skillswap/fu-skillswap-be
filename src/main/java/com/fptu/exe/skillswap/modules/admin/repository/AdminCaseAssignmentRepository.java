package com.fptu.exe.skillswap.modules.admin.repository;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminCaseAssignmentRepository extends JpaRepository<AdminCaseAssignment, UUID> {

    Optional<AdminCaseAssignment> findByCaseTypeAndCaseId(String caseType, UUID caseId);

    List<AdminCaseAssignment> findByCaseTypeAndCaseIdIn(String caseType, Collection<UUID> caseIds);
}
