package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryActivityResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryResponse;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.VisibleUserSummary;
import com.fptu.exe.skillswap.modules.mentor.port.MentorAdminPort;
import com.fptu.exe.skillswap.modules.payment.port.PaymentAdminPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserSummaryService {

    private final UserAdminPort userAdminPort;
    private final MentorAdminPort mentorAdminPort;
    private final BookingQueryPort bookingQueryPort;
    private final PaymentAdminPort paymentAdminPort;
    private final ForumAdminPort forumAdminPort;

    public AdminUserSummaryResponse getSummary(UUID userId) {
        VisibleUserSummary user = userAdminPort.findVisibleUserSummary(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy user visible"));

        return new AdminUserSummaryResponse(
                user.userId(),
                user.email(),
                user.fullName(),
                user.avatarUrl(),
                user.status(),
                user.roles(),
                user.lastLoginAt(),
                user.createdAt(),
                userAdminPort.getAcademicProfileSummary(userId),
                mentorAdminPort.getMentorProfileSummary(userId),
                new AdminUserSummaryActivityResponse(
                        bookingQueryPort.countByMenteeId(userId),
                        bookingQueryPort.countByMentorProfileUserId(userId),
                        paymentAdminPort.countTotalPaymentOrdersByUserId(userId),
                        paymentAdminPort.countTotalPayoutRequestsByUserId(userId),
                        forumAdminPort.countReportsCreatedBy(userId)
                )
        );
    }

}
