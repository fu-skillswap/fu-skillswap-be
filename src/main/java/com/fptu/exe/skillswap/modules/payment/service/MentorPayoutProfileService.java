package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.MentorPayoutProfile;
import com.fptu.exe.skillswap.modules.payment.dto.request.MentorPayoutProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.MentorPayoutProfileResponse;
import com.fptu.exe.skillswap.modules.payment.repository.MentorPayoutProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorPayoutProfileService {

    private final MentorPayoutProfileRepository payoutProfileRepository;

    @Transactional
    public MentorPayoutProfileResponse create(UUID mentorUserId, MentorPayoutProfileUpsertRequest request) {
        validateRequest(request);
        MentorPayoutProfile profile = MentorPayoutProfile.builder()
                .mentorUserId(mentorUserId)
                .accountHolderName(request.accountHolderName().trim())
                .bankCode(normalize(request.bankCode()))
                .bankName(request.bankName().trim())
                .accountNumber(normalizeAccountNumber(request.accountNumber()))
                .isDefault(Boolean.TRUE.equals(request.isDefault()) || payoutProfileRepository.countByMentorUserIdAndIsDefaultTrue(mentorUserId) == 0)
                .isActive(request.isActive() == null || request.isActive())
                .build();
        if (profile.isDefault()) {
            unsetOtherDefaults(mentorUserId);
        }
        return toResponse(payoutProfileRepository.save(profile));
    }

    @Transactional
    public MentorPayoutProfileResponse update(UUID mentorUserId, UUID payoutProfileId, MentorPayoutProfileUpsertRequest request) {
        validateRequest(request);
        MentorPayoutProfile profile = payoutProfileRepository.findByIdAndMentorUserId(payoutProfileId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payout profile"));
        profile.setAccountHolderName(request.accountHolderName().trim());
        profile.setBankCode(normalize(request.bankCode()));
        profile.setBankName(request.bankName().trim());
        profile.setAccountNumber(normalizeAccountNumber(request.accountNumber()));
        profile.setActive(request.isActive() == null || request.isActive());
        boolean shouldBeDefault = Boolean.TRUE.equals(request.isDefault());
        profile.setDefault(shouldBeDefault);
        if (shouldBeDefault) {
            unsetOtherDefaults(mentorUserId);
            profile.setDefault(true);
        }
        if (!profile.isActive()) {
            profile.setDefault(false);
        }
        return toResponse(payoutProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public List<MentorPayoutProfileResponse> getMine(UUID mentorUserId) {
        return payoutProfileRepository.findByMentorUserIdOrderByCreatedAtDesc(mentorUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorPayoutProfile getActiveProfileForPayout(UUID mentorUserId, UUID payoutProfileId) {
        MentorPayoutProfile profile = payoutProfileId != null
                ? payoutProfileRepository.findByIdAndMentorUserId(payoutProfileId, mentorUserId)
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payout profile đã chọn"))
                : payoutProfileRepository.findFirstByMentorUserIdAndIsDefaultTrueAndIsActiveTrue(mentorUserId)
                    .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Mentor chưa có payout profile mặc định đang hoạt động"));
        if (!profile.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Payout profile đã chọn hiện không hoạt động");
        }
        return profile;
    }

    private void unsetOtherDefaults(UUID mentorUserId) {
        payoutProfileRepository.findByMentorUserIdOrderByCreatedAtDesc(mentorUserId).forEach(profile -> {
            if (profile.isDefault()) {
                profile.setDefault(false);
                payoutProfileRepository.save(profile);
            }
        });
    }

    private MentorPayoutProfileResponse toResponse(MentorPayoutProfile profile) {
        return MentorPayoutProfileResponse.builder()
                .payoutProfileId(profile.getId())
                .mentorUserId(profile.getMentorUserId())
                .accountHolderName(profile.getAccountHolderName())
                .bankCode(profile.getBankCode())
                .bankName(profile.getBankName())
                .accountNumberMasked(maskAccountNumber(profile.getAccountNumber()))
                .isDefault(profile.isDefault())
                .isActive(profile.isActive())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private void validateRequest(MentorPayoutProfileUpsertRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thông tin payout profile không được để trống");
        }
        String accountNumber = normalizeAccountNumber(request.accountNumber());
        if (accountNumber.length() < 6 || accountNumber.length() > 30) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số tài khoản phải dài từ 6 đến 30 chữ số");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return "";
        }
        String normalized = accountNumber.replaceAll("\\s+", "");
        if (!normalized.matches("\\d+")) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số tài khoản chỉ được chứa chữ số");
        }
        return normalized;
    }

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(Math.max(0, accountNumber.length() - 4)) + accountNumber.substring(accountNumber.length() - 4);
    }
}
