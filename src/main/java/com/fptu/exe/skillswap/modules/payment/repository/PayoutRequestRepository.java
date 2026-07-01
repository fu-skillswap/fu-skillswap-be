package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequest;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payoutRequest from PayoutRequest payoutRequest where payoutRequest.id = :id")
    java.util.Optional<PayoutRequest> findByIdForUpdate(@Param("id") UUID id);

    List<PayoutRequest> findByMentorUserIdOrderByRequestedAtDesc(UUID mentorUserId);

    List<PayoutRequest> findByStatusOrderByRequestedAtDesc(PayoutRequestStatus status);

    boolean existsByMentorUserIdAndStatusIn(UUID mentorUserId, java.util.Collection<PayoutRequestStatus> statuses);

    @Query(value = """
            select payoutRequest
            from PayoutRequest payoutRequest
            where (:status is null or payoutRequest.status = :status)
              and (:mentorUserId is null or payoutRequest.mentorUserId = :mentorUserId)
            """, countQuery = """
            select count(payoutRequest.id)
            from PayoutRequest payoutRequest
            where (:status is null or payoutRequest.status = :status)
              and (:mentorUserId is null or payoutRequest.mentorUserId = :mentorUserId)
            """)
    Page<PayoutRequest> searchForAdmin(
            @Param("status") PayoutRequestStatus status,
            @Param("mentorUserId") UUID mentorUserId,
            Pageable pageable
    );
}
