package com.fptu.exe.skillswap.modules.course.dto;

/** Provider-neutral webhook data consumed by the Course workflow. */
public record CourseVideoWebhook(String videoId, String libraryId, int status) {
}
