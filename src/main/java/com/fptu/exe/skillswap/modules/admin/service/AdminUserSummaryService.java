package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryAcademicProfileResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryActivityResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryMentorProfileResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PayoutRequestRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserSummaryService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final BookingRepository bookingRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final ForumReportRepository forumReportRepository;

    public AdminUserSummaryResponse getSummary(UUID userId) {
        User user = userRepository.findAdminVisibleUserById(
                        userId,
                        RoleCode.MENTEE,
                        RoleCode.MENTOR,
                        RoleCode.ADMIN,
                        RoleCode.SYSTEM_ADMIN
                )
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy user visible"));

        StudentProfile studentProfile = studentProfileRepository.findWithDetailsByUserId(userId).orElse(null);
        MentorProfile mentorProfile = mentorProfileRepository.findById(userId).orElse(null);

        return new AdminUserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getStatus().name(),
                extractVisibleRoles(user),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                toAcademicProfile(studentProfile),
                toMentorProfile(mentorProfile),
                new AdminUserSummaryActivityResponse(
                        bookingRepository.countByMenteeId(userId),
                        bookingRepository.countByMentorProfileUserId(userId),
                        paymentOrderRepository.countByPayerUserId(userId),
                        payoutRequestRepository.countByMentorUserId(userId),
                        forumReportRepository.countCreatedByReporterUserId(userId)
                )
        );
    }

    private List<String> extractVisibleRoles(User user) {
        List<String> roles = new ArrayList<>();
        if (user.getRoles() == null) {
            return roles;
        }
        if (user.getRoles().contains(RoleCode.MENTEE)) {
            roles.add(RoleCode.MENTEE.name());
        }
        if (user.getRoles().contains(RoleCode.MENTOR)) {
            roles.add(RoleCode.MENTOR.name());
        }
        return roles;
    }

    private AdminUserSummaryAcademicProfileResponse toAcademicProfile(StudentProfile profile) {
        if (profile == null) {
            return null;
        }
        return new AdminUserSummaryAcademicProfileResponse(
                profile.getClaimedStudentCode(),
                profile.getCampus() == null || profile.getCampus().getCode() == null ? null : profile.getCampus().getCode().name(),
                profile.getCampus() == null ? null : profile.getCampus().getName(),
                profile.getProgram() == null ? null : profile.getProgram().getCode(),
                profile.getProgram() == null ? null : profile.getProgram().getNameVi(),
                profile.getSpecialization() == null ? null : profile.getSpecialization().getCode(),
                profile.getSpecialization() == null ? null : profile.getSpecialization().getNameVi(),
                profile.getSemester(),
                profile.isAlumni()
        );
    }

    private AdminUserSummaryMentorProfileResponse toMentorProfile(MentorProfile profile) {
        if (profile == null) {
            return new AdminUserSummaryMentorProfileResponse(false, null, null, null, null, null, null);
        }
        return new AdminUserSummaryMentorProfileResponse(
                true,
                profile.getStatus() == null ? null : profile.getStatus().name(),
                profile.isAvailable(),
                profile.getVerifiedAt(),
                profile.getHeadline(),
                profile.getAverageRating(),
                profile.getTotalCompletedSessions()
        );
    }
}
