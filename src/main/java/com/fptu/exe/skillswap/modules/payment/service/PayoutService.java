package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequest;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import com.fptu.exe.skillswap.modules.payment.domain.MentorPayoutProfile;
import com.fptu.exe.skillswap.modules.payment.dto.request.PayoutRequestCreateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PayoutRequestResponse;
import com.fptu.exe.skillswap.modules.payment.repository.PayoutRequestRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutService {

    private final SettlementService settlementService;
    private final PayoutRequestRepository payoutRequestRepository;
    private final MentorPayoutProfileService payoutProfileService;

    @Transactional
    public PayoutRequestResponse createRequest(UUID mentorUserId, PayoutRequestCreateRequest request) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || request.amountScoin() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "amountScoin không được để trống");
        }
        int available = settlementService.getMentorAvailableSettlement(mentorUserId);
        if (request.amountScoin() > available) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Số dư settlement không đủ để tạo payout request");
        }
        MentorPayoutProfile payoutProfile = payoutProfileService.getActiveProfileForPayout(mentorUserId, request.payoutProfileId());
        PayoutRequest payoutRequest = payoutRequestRepository.save(PayoutRequest.builder()
                .mentorUserId(mentorUserId)
                .settlementAccountId(settlementService.ensureMentorAccount(mentorUserId).getId())
                .payoutProfileId(payoutProfile.getId())
                .amountScoin(request.amountScoin())
                .status(PayoutRequestStatus.REQUESTED)
                .bankAccountNameSnapshot(payoutProfile.getAccountHolderName())
                .bankNameSnapshot(payoutProfile.getBankName())
                .bankAccountNumberMaskedSnapshot(MentorPayoutProfileService.maskAccountNumber(payoutProfile.getAccountNumber()))
                .requestedAt(DateTimeUtil.now())
                .build());
        return toResponse(payoutRequest);
    }

    @Transactional
    public PayoutRequestResponse approve(UUID adminUserId, UUID payoutRequestId, String note) {
        PayoutRequest payoutRequest = load(payoutRequestId);
        if (payoutRequest.getStatus() != PayoutRequestStatus.REQUESTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể duyệt payout request đang REQUESTED");
        }
        settlementService.holdPayout(payoutRequest.getMentorUserId(), payoutRequest.getId(), payoutRequest.getAmountScoin(), note);
        payoutRequest.setStatus(PayoutRequestStatus.APPROVED);
        payoutRequest.setAdminUserId(adminUserId);
        payoutRequest.setAdminNote(note);
        payoutRequest.setReviewedAt(DateTimeUtil.now());
        payoutRequest.setApprovedAt(DateTimeUtil.now());
        return toResponse(payoutRequestRepository.save(payoutRequest));
    }

    @Transactional
    public PayoutRequestResponse reject(UUID adminUserId, UUID payoutRequestId, String note) {
        PayoutRequest payoutRequest = load(payoutRequestId);
        PayoutRequestStatus currentStatus = payoutRequest.getStatus();
        if (currentStatus != PayoutRequestStatus.REQUESTED && currentStatus != PayoutRequestStatus.APPROVED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể từ chối payout request đang chờ xử lý");
        }
        payoutRequest.setStatus(PayoutRequestStatus.REJECTED);
        payoutRequest.setAdminUserId(adminUserId);
        payoutRequest.setAdminNote(note);
        payoutRequest.setReviewedAt(DateTimeUtil.now());
        payoutRequest.setRejectedAt(DateTimeUtil.now());
        if (currentStatus == PayoutRequestStatus.APPROVED) {
            settlementService.voidPayoutHold(payoutRequest.getMentorUserId(), payoutRequest.getId(), "Rollback payout request " + payoutRequest.getId());
        }
        return toResponse(payoutRequestRepository.save(payoutRequest));
    }

    @Transactional
    public PayoutRequestResponse markPaid(UUID adminUserId, UUID payoutRequestId, String note) {
        PayoutRequest payoutRequest = load(payoutRequestId);
        if (payoutRequest.getStatus() != PayoutRequestStatus.APPROVED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể đánh dấu paid cho payout request đã duyệt");
        }
        payoutRequest.setStatus(PayoutRequestStatus.PAID);
        payoutRequest.setAdminUserId(adminUserId);
        payoutRequest.setAdminNote(note);
        payoutRequest.setPaidAt(DateTimeUtil.now());
        settlementService.finalizePayout(payoutRequest.getMentorUserId(), payoutRequest.getId(), "Finalize payout request " + payoutRequest.getId());
        return toResponse(payoutRequestRepository.save(payoutRequest));
    }

    @Transactional(readOnly = true)
    public List<PayoutRequestResponse> getByMentor(UUID mentorUserId) {
        return payoutRequestRepository.findByMentorUserIdOrderByRequestedAtDesc(mentorUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    private PayoutRequest load(UUID payoutRequestId) {
        return payoutRequestRepository.findById(payoutRequestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payout request"));
    }

    private PayoutRequestResponse toResponse(PayoutRequest payoutRequest) {
        return PayoutRequestResponse.builder()
                .payoutRequestId(payoutRequest.getId())
                .mentorUserId(payoutRequest.getMentorUserId())
                .settlementAccountId(payoutRequest.getSettlementAccountId())
                .payoutProfileId(payoutRequest.getPayoutProfileId())
                .amountScoin(payoutRequest.getAmountScoin())
                .status(payoutRequest.getStatus())
                .bankAccountNameSnapshot(payoutRequest.getBankAccountNameSnapshot())
                .bankNameSnapshot(payoutRequest.getBankNameSnapshot())
                .bankAccountNumberMaskedSnapshot(payoutRequest.getBankAccountNumberMaskedSnapshot())
                .adminUserId(payoutRequest.getAdminUserId())
                .adminNote(payoutRequest.getAdminNote())
                .requestedAt(payoutRequest.getRequestedAt())
                .reviewedAt(payoutRequest.getReviewedAt())
                .approvedAt(payoutRequest.getApprovedAt())
                .paidAt(payoutRequest.getPaidAt())
                .rejectedAt(payoutRequest.getRejectedAt())
                .build();
    }
}
