package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequest;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    List<PayoutRequest> findByMentorUserIdOrderByRequestedAtDesc(UUID mentorUserId);

    List<PayoutRequest> findByStatusOrderByRequestedAtDesc(PayoutRequestStatus status);

    boolean existsByMentorUserIdAndStatusIn(UUID mentorUserId, java.util.Collection<PayoutRequestStatus> statuses);
}
