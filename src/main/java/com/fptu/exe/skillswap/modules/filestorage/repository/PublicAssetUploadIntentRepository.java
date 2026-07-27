package com.fptu.exe.skillswap.modules.filestorage.repository;

import com.fptu.exe.skillswap.modules.filestorage.domain.PublicAssetUploadIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface PublicAssetUploadIntentRepository extends JpaRepository<PublicAssetUploadIntent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PublicAssetUploadIntent i where i.id = :id")
    Optional<PublicAssetUploadIntent> findByIdForUpdate(@Param("id") UUID id);
}
