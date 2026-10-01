package com.fptu.exe.skillswap.modules.system.repository;

import com.fptu.exe.skillswap.modules.system.domain.SystemAppVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemAppVersionRepository extends JpaRepository<SystemAppVersion, Long> {

    Optional<SystemAppVersion> findByPlatformIgnoreCase(String platform);
}
