package com.fptu.exe.skillswap.modules.academic.repository;

import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampusRepository extends JpaRepository<Campus, UUID> {
    Optional<Campus> findByCode(CampusCode code);
    List<Campus> findByIsActiveTrue();
}
