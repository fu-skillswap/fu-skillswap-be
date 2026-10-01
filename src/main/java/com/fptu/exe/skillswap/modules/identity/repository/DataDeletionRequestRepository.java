package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.DataDeletionRequest;
import com.fptu.exe.skillswap.modules.identity.domain.DataDeletionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DataDeletionRequestRepository extends JpaRepository<DataDeletionRequest, UUID> {
    List<DataDeletionRequest> findByEmailOrderByCreatedAtDesc(String email);
    List<DataDeletionRequest> findByStatusOrderByCreatedAtAsc(DataDeletionRequestStatus status);
}
