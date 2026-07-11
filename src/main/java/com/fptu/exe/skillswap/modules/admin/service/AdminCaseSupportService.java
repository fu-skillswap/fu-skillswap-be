package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PayoutRequestRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCaseSupportService {

    private final UserRepository userRepository;
    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final BookingRepository bookingRepository;
    private final ForumReportRepository forumReportRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final EmailOutboxRepository emailOutboxRepository;

    public User requireAdminUser(UUID adminUserId) {
        if (adminUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người quản trị"));
    }

    public void assertCaseExists(AdminCaseType caseType, UUID caseId) {
        if (caseId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "caseId không hợp lệ");
        }

        boolean exists = switch (caseType) {
            case MENTOR_VERIFICATION_REQUEST -> mentorVerificationRequestRepository.existsById(caseId);
            case BOOKING -> bookingRepository.existsById(caseId);
            case FORUM_REPORT -> forumReportRepository.existsById(caseId);
            case PAYOUT_REQUEST -> payoutRequestRepository.existsById(caseId);
            case PAYMENT_ORDER -> paymentOrderRepository.existsById(caseId);
            case EMAIL_OUTBOX -> emailOutboxRepository.existsById(caseId);
        };

        if (!exists) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy case cần thao tác");
        }
    }
}
