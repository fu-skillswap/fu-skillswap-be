package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorAdminPort;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorAdminDetailDto;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorAdminFilterQuery;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorAdminListItemDto;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMentorModerationService {

    private final MentorAdminPort mentorAdminPort;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorListItemResponse> getMentors(AdminMentorListRequest request) {
        MentorAdminFilterQuery query = new MentorAdminFilterQuery();
        if (request != null) {
            query.setKeyword(request.getKeyword());
            query.setStatus(request.getStatus());
            query.setIsAvailable(request.getIsAvailable());
            query.setPage(request.getPage());
            query.setSize(request.getSize());
            query.setSortBy(request.getSortBy());
            query.setDirection(request.getDirection());
        }
        PageResponse<MentorAdminListItemDto> result = mentorAdminPort.getMentors(query);
        return PageResponse.<AdminMentorListItemResponse>builder()
                .content(result.getContent().stream().map(this::toListItemResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminMentorDetailResponse getMentorDetail(UUID mentorUserId) {
        MentorAdminDetailDto dto = mentorAdminPort.getMentorDetail(mentorUserId);
        if (dto == null) {
            return null;
        }
        return AdminMentorDetailResponse.builder()
                .mentorUserId(dto.mentorUserId())
                .email(dto.email())
                .displayName(dto.displayName())
                .avatarUrl(dto.avatarUrl())
                .phoneNumber(dto.phoneNumber())
                .userStatus(dto.userStatus())
                .mentorStatus(dto.mentorStatus())
                .isAvailable(dto.isAvailable())
                .bookingSuspendedUntil(dto.bookingSuspendedUntil())
                .headline(dto.headline())
                .expertiseDescription(dto.expertiseDescription())
                .subjectResults(dto.subjectResults())
                .foundationSupportLevel(dto.foundationSupportLevel())
                .outputReviewSupportLevel(dto.outputReviewSupportLevel())
                .directionSupportLevel(dto.directionSupportLevel())
                .featuredProjects(dto.featuredProjects())
                .achievements(dto.achievements())
                .supportingSubjects(dto.supportingSubjects())
                .teachingMode(dto.teachingMode())
                .sessionDuration(dto.sessionDuration())
                .ratingAverage(dto.ratingAverage())
                .reviewCount(dto.reviewCount())
                .completedSessions(dto.completedSessions())
                .rejectedBookings(dto.rejectedBookings())
                .portfolioUrl(dto.portfolioUrl())
                .linkedinUrl(dto.linkedinUrl())
                .githubUrl(dto.githubUrl())
                .primaryLabel(dto.primaryLabel())
                .verifiedAt(dto.verifiedAt())
                .createdAt(dto.createdAt())
                .updatedAt(dto.updatedAt())
                .build();
    }

    private AdminMentorListItemResponse toListItemResponse(MentorAdminListItemDto dto) {
        return new AdminMentorListItemResponse(
                dto.mentorUserId(),
                dto.fullName(),
                dto.email(),
                dto.avatarUrl(),
                dto.programCode(),
                dto.totalCompletedSessions(),
                dto.averageRating(),
                dto.status(),
                dto.createdAt()
        );
    }
}
