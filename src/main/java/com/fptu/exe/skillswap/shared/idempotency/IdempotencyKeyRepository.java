package com.fptu.exe.skillswap.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Modifying
    @Query("delete from IdempotencyKey k where k.createdAt < :expirationTime")
    int deleteByCreatedAtBefore(@Param("expirationTime") LocalDateTime expirationTime);
}
