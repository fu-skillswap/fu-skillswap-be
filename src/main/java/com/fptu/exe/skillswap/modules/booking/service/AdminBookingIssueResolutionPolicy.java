package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.port.dto.BookingAdminResolveIssueCommand;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

/** Pure validation for the controlled administrator settlement matrix. */
public final class AdminBookingIssueResolutionPolicy {

    public static final int TOTAL_BPS = 10_000;

    private AdminBookingIssueResolutionPolicy() {
    }

    public static void validate(BookingAdminResolveIssueCommand request, BookingIssueType issueType) {
        if (request == null || request.action() == null || request.reasonCode() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "action và reasonCode là bắt buộc");
        }
        AdminBookingIssueResolutionAction action = request.action();
        if (requiresAdminNote(action, request.reasonCode()) && isBlank(request.adminNote())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "adminNote là bắt buộc cho quyết định này");
        }
        if (action == AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT) {
            requireNonNoShowIssue(issueType, action);
            requirePartialReason(request.reasonCode());
            int mentee = requireBps(request.menteeBps(), "menteeBps");
            int mentor = requireBps(request.mentorBps(), "mentorBps");
            int platform = requireBps(request.platformBps(), "platformBps");
            if (mentee + mentor + platform != TOTAL_BPS) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Tổng menteeBps, mentorBps và platformBps phải bằng 10000");
            }
            return;
        }
        if (request.menteeBps() != null || request.mentorBps() != null || request.platformBps() != null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ PARTIAL_SETTLEMENT mới nhận tỷ lệ chia tiền");
        }
        if (action == AdminBookingIssueResolutionAction.RELEASE_AS_IS) {
            requireNonNoShowIssue(issueType, action);
        }
        if (action == AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND
                || action == AdminBookingIssueResolutionAction.CONFIRM_MENTEE_NO_SHOW_RELEASE) {
            if (issueType != BookingIssueType.MENTOR_NO_SHOW && issueType != BookingIssueType.MENTEE_NO_SHOW) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Action no-show chỉ áp dụng cho dispute no-show");
            }
            if (request.reasonCode() != AdminBookingIssueResolutionReasonCode.NO_SHOW_CONFIRMED) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Action no-show phải dùng reasonCode NO_SHOW_CONFIRMED");
            }
        }
        if (action == AdminBookingIssueResolutionAction.CONFIRM_SESSION
                && request.reasonCode() != AdminBookingIssueResolutionReasonCode.SESSION_CONFIRMED) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "CONFIRM_SESSION phải dùng reasonCode SESSION_CONFIRMED");
        }
    }

    public static boolean isPartial(AdminBookingIssueResolutionAction action) {
        return action == AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT;
    }

    private static void requireNonNoShowIssue(BookingIssueType issueType, AdminBookingIssueResolutionAction action) {
        if (issueType == BookingIssueType.MENTOR_NO_SHOW || issueType == BookingIssueType.MENTEE_NO_SHOW) {
            throw new BaseException(ErrorCode.BAD_REQUEST, action + " không áp dụng cho dispute no-show");
        }
    }

    private static void requirePartialReason(AdminBookingIssueResolutionReasonCode reasonCode) {
        if (reasonCode != AdminBookingIssueResolutionReasonCode.QUALITY_PARTIAL_COMPENSATION
                && reasonCode != AdminBookingIssueResolutionReasonCode.TECHNICAL_PARTIAL_COMPENSATION
                && reasonCode != AdminBookingIssueResolutionReasonCode.OTHER) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "PARTIAL_SETTLEMENT cần reasonCode phù hợp với bồi hoàn một phần");
        }
    }

    private static boolean requiresAdminNote(AdminBookingIssueResolutionAction action,
                                             AdminBookingIssueResolutionReasonCode reasonCode) {
        return action == AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT
                || reasonCode == AdminBookingIssueResolutionReasonCode.OTHER;
    }

    private static int requireBps(Integer value, String field) {
        if (value == null || value < 0 || value > TOTAL_BPS) {
            throw new BaseException(ErrorCode.BAD_REQUEST, field + " phải nằm trong khoảng 0 đến 10000");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
