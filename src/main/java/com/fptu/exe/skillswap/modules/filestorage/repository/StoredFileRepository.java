package com.fptu.exe.skillswap.modules.filestorage.repository;

import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    boolean existsByOwnerIdAndPurposeAndPublicUrl(UUID ownerId, FilePurpose purpose, String publicUrl);
}
