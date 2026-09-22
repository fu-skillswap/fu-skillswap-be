package com.fptu.exe.skillswap.modules.mentor.service.discovery;

public final class RecommendationReasonTextMapper {

    private RecommendationReasonTextMapper() {
    }

    public static String toVietnamese(RecommendationReason reason) {
        return switch (reason.code()) {
            case HIGH_RATING -> "Được đánh giá cao từ mentee";
            case TRUSTED_REVIEW_VOLUME -> "Có lượng đánh giá đủ tin cậy";
            case MENTORING_EXPERIENCE -> "Đã có kinh nghiệm mentoring thực tế";
            case STABLE_ACCEPTANCE_RATE -> "Tỷ lệ chấp nhận yêu cầu ổn định";
            case LOW_CANCELLATION_RATE -> "Ít hủy lịch sau khi đã nhận";
            case RECENT_ACTIVITY -> "Hoạt động gần đây";
            case ACTIVE_SERVICES -> "Có " + (reason.count() == null ? 0 : reason.count()) + " dịch vụ đang hoạt động";
            case HAS_AVAILABILITY -> "Có lịch rảnh khả dụng";
            case PREFERRED_DURATION_AVAILABLE -> "Có slot phù hợp đúng thời lượng mentee muốn book";
            case SUBJECT_FIT -> "Khớp kiểu mentor mạnh đúng phần đang cần";
            case SIMILAR_MENTORING_EXPERIENCE -> "Mentor đã có trải nghiệm mentoring thực tế";
            case DECLARED_NEEDS_MATCH -> "Khớp nhu cầu mentoring đã khai báo";
            case COMPLETED_SESSION -> "Đã có phiên mentoring hoàn thành";
            case DEFAULT_DISCOVERY_MATCH -> "Phù hợp với các tiêu chí discovery hiện tại";
        };
    }
}
