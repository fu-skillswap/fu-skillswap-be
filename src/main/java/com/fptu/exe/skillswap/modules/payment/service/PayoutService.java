package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequest;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import com.fptu.exe.skillswap.modules.payment.domain.MentorPayoutProfile;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminPayoutRequestListRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PayoutRequestCreateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PayoutRequestResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.modules.payment.repository.PayoutRequestRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutService {

    private final SettlementService settlementService;
    private final PayoutRequestRepository payoutRequestRepository;
    private final MentorPayoutProfileService payoutProfileService;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public PayoutRequestResponse createRequest(UUID mentorUserId, PayoutRequestCreateRequest request) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || request.amountScoin() == null || request.amountScoin() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "amountScoin phải lớn hơn 0");
        }
        MentorPayoutProfile payoutProfile = payoutProfileService.getActiveProfileForPayout(mentorUserId, request.payoutProfileId());
        Instant nowUtc = timeProvider.instant();
        PayoutRequest payoutRequest = payoutRequestRepository.save(PayoutRequest.builder()
                .mentorUserId(mentorUserId)
                .settlementAccountId(settlementService.ensureMentorAccount(mentorUserId).getId())
                .payoutProfileId(payoutProfile.getId())
                .amountScoin(request.amountScoin())
                .status(PayoutRequestStatus.REQUESTED)
                .bankAccountNameSnapshot(payoutProfile.getAccountHolderName())
                .bankNameSnapshot(payoutProfile.getBankName())
                .bankAccountNumberMaskedSnapshot(MentorPayoutProfileService.maskAccountNumber(payoutProfile.getAccountNumber()))
                .requestedAtUtc(nowUtc)
                .requestedAt(timeProvider.nowBusiness())
                .build());
        // A REQUESTED payout already reserves funds; otherwise concurrent requests can over-commit the ledger.
        settlementService.holdPayout(mentorUserId, payoutRequest.getId(), payoutRequest.getAmountScoin(), request.note());
        return toResponse(payoutRequest);
    }

    @Transactional
    public PayoutRequestResponse approve(UUID adminUserId, UUID payoutRequestId, String note) {
        PayoutRequest payoutRequest = loadForUpdate(payoutRequestId);
        if (payoutRequest.getStatus() != PayoutRequestStatus.REQUESTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể duyệt payout request đang REQUESTED");
        }
        // Supports legacy REQUESTED records created before request-time holds. New requests already have a HOLD.
        settlementService.holdPayout(payoutRequest.getMentorUserId(), payoutRequest.getId(), payoutRequest.getAmountScoin(), note);
        payoutRequest.setStatus(PayoutRequestStatus.APPROVED);
        payoutRequest.setAdminUserId(adminUserId);
        payoutRequest.setAdminNote(note);
        Instant nowUtc = timeProvider.instant();
        payoutRequest.setReviewedAtUtc(nowUtc);
        payoutRequest.setReviewedAt(timeProvider.nowBusiness());
        payoutRequest.setApprovedAtUtc(nowUtc);
        payoutRequest.setApprovedAt(timeProvider.nowBusiness());
        return toResponse(payoutRequestRepository.save(payoutRequest));
    }

    @Transactional
    public PayoutRequestResponse reject(UUID adminUserId, UUID payoutRequestId, String note) {
        PayoutRequest payoutRequest = loadForUpdate(payoutRequestId);
        PayoutRequestStatus currentStatus = payoutRequest.getStatus();
        if (currentStatus != PayoutRequestStatus.REQUESTED && currentStatus != PayoutRequestStatus.APPROVED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể từ chối payout request đang chờ xử lý");
        }
        payoutRequest.setStatus(PayoutRequestStatus.REJECTED);
        payoutRequest.setAdminUserId(adminUserId);
        payoutRequest.setAdminNote(note);
        Instant nowUtc = timeProvider.instant();
        payoutRequest.setReviewedAtUtc(nowUtc);
        payoutRequest.setReviewedAt(timeProvider.nowBusiness());
        payoutRequest.setRejectedAtUtc(nowUtc);
        payoutRequest.setRejectedAt(timeProvider.nowBusiness());
        settlementService.voidPayoutHold(payoutRequest.getMentorUserId(), payoutRequest.getId(), "Rollback payout request " + payoutRequest.getId());
        return toResponse(payoutRequestRepository.save(payoutRequest));
    }

    @Transactional
    public PayoutRequestResponse markPaid(UUID adminUserId, UUID payoutRequestId, String note) {
        PayoutRequest payoutRequest = loadForUpdate(payoutRequestId);
        if (payoutRequest.getStatus() != PayoutRequestStatus.APPROVED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể đánh dấu paid cho payout request đã duyệt");
        }
        payoutRequest.setStatus(PayoutRequestStatus.PAID);
        payoutRequest.setAdminUserId(adminUserId);
        payoutRequest.setAdminNote(note);
        payoutRequest.setPaidAtUtc(timeProvider.instant());
        payoutRequest.setPaidAt(timeProvider.nowBusiness());
        settlementService.finalizePayout(payoutRequest.getMentorUserId(), payoutRequest.getId(), "Finalize payout request " + payoutRequest.getId());
        return toResponse(payoutRequestRepository.save(payoutRequest));
    }

    @Transactional(readOnly = true)
    public List<PayoutRequestResponse> getByMentor(UUID mentorUserId) {
        return payoutRequestRepository.findByMentorUserIdOrderByRequestedAtDesc(mentorUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutRequestResponse> getAdminPayoutRequests(AdminPayoutRequestListRequest request) {
        AdminPayoutRequestListRequest safeRequest = request == null ? new AdminPayoutRequestListRequest() : request;
        Page<PayoutRequest> page = payoutRequestRepository.searchForAdmin(
                safeRequest.getStatus(),
                safeRequest.getMentorUserId(),
                adminPageable(safeRequest)
        );

        return PageResponse.<PayoutRequestResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public PayoutRequestResponse getAdminPayoutRequestDetail(UUID payoutRequestId) {
        return toResponse(load(payoutRequestId));
    }

    private PayoutRequest load(UUID payoutRequestId) {
        return payoutRequestRepository.findById(payoutRequestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payout request"));
    }

    private PayoutRequest loadForUpdate(UUID payoutRequestId) {
        return payoutRequestRepository.findByIdForUpdate(payoutRequestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payout request"));
    }

    private Pageable adminPageable(AdminPayoutRequestListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        return PageRequest.of(page, size, Sort.by(request.resolveDirection(), adminSortBy(request.getSortBy())));
    }

    private String adminSortBy(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            case "status" -> "status";
            case "approvedAt" -> "approvedAt";
            case "paidAt" -> "paidAt";
            case "rejectedAt" -> "rejectedAt";
            default -> "requestedAt";
        };
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
                .requestedAt(toOffset(payoutRequest.getRequestedAtUtc(), payoutRequest.getRequestedAt()))
                .reviewedAt(toOffset(payoutRequest.getReviewedAtUtc(), payoutRequest.getReviewedAt()))
                .approvedAt(toOffset(payoutRequest.getApprovedAtUtc(), payoutRequest.getApprovedAt()))
                .paidAt(toOffset(payoutRequest.getPaidAtUtc(), payoutRequest.getPaidAt()))
                .rejectedAt(toOffset(payoutRequest.getRejectedAtUtc(), payoutRequest.getRejectedAt()))
                .build();
    }

    private static OffsetDateTime toOffset(Instant utc, java.time.LocalDateTime legacy) {
        if (utc != null) return com.fptu.exe.skillswap.shared.time.BusinessTime.toOffsetDateTime(utc);
        if (legacy != null) return com.fptu.exe.skillswap.shared.time.BusinessTime.toOffsetDateTime(legacy);
        return null;
    }
}
