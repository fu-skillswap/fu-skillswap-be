package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.AdminUserReference;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorVerificationAdminPort;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminPort;
import com.fptu.exe.skillswap.modules.payment.port.PaymentAdminPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCaseSupportService {

    private final UserAdminPort userAdminPort;
    private final MentorVerificationAdminPort mentorVerificationAdminPort;
    private final BookingQueryPort bookingQueryPort;
    private final ForumAdminPort forumAdminPort;
    private final PaymentAdminPort paymentAdminPort;
    private final EmailOutboxAdminPort emailOutboxAdminPort;

    public AdminUserReference requireAdminUser(UUID adminUserId) {
        if (adminUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return userAdminPort.requireAdminReference(adminUserId);
    }

    public String findAdminDisplayName(UUID adminUserId) {
        return userAdminPort.findReference(adminUserId).map(AdminUserReference::displayName).orElse(null);
    }

    public void assertCaseExists(AdminCaseType caseType, UUID caseId) {
        if (caseId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "caseId không hợp lệ");
        }

        boolean exists = switch (caseType) {
            case MENTOR_VERIFICATION_REQUEST -> mentorVerificationAdminPort.existsById(caseId);
            case BOOKING -> bookingQueryPort.existsById(caseId);
            case FORUM_REPORT -> forumAdminPort.existsReportById(caseId);
            case PAYOUT_REQUEST -> paymentAdminPort.existsPayoutRequestById(caseId);
            case PAYMENT_ORDER -> paymentAdminPort.existsPaymentOrderById(caseId);
            case EMAIL_OUTBOX -> emailOutboxAdminPort.existsById(caseId);
        };

        if (!exists) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy case cần thao tác");
        }
    }
}
