package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Academic, project, and public knowledge evidence for a mentor.")
public record MentorEvidenceResponse(
        MentorEducationResponse education,
        List<MentorSubjectResultResponse> subjectResults,
        List<MentorFeaturedProjectResponse> featuredProjects,
        List<MentorAchievementResponse> achievements,
        String portfolioUrl,
        String githubUrl,
        MentorAuthorityContentResponse authorityContent
) {
}
