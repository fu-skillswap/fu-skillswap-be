package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;

import java.util.List;

public record MentorEnrichedData(
        List<MentorSubjectResultResponse> subjectResults,
        List<MentorFeaturedProjectResponse> featuredProjects,
        List<MentorAchievementResponse> achievements,
        List<MentorService> services,
        boolean hasAvailability,
        boolean hasPreferredDurationAvailability
) {
    public static MentorEnrichedData empty() {
        return new MentorEnrichedData(List.of(), List.of(), List.of(), List.of(), false, false);
    }
}
