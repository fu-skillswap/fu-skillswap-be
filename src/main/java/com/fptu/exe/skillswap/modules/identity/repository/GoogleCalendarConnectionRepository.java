package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnection;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface GoogleCalendarConnectionRepository extends JpaRepository<GoogleCalendarConnection, UUID> {

    Optional<GoogleCalendarConnection> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GoogleCalendarConnection c join fetch c.user u where u.id = :userId")
    Optional<GoogleCalendarConnection> findByUserIdForUpdate(@Param("userId") UUID userId);

    boolean existsByUserIdAndConnectionStatus(UUID userId, GoogleCalendarConnectionStatus connectionStatus);
}
