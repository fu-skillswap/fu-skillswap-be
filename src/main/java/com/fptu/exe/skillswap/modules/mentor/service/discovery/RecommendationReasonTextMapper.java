package com.fptu.exe.skillswap.modules.mentor.service.discovery;

public final class RecommendationReasonTextMapper {

    private RecommendationReasonTextMapper() {
    }

    public static String toVietnamese(RecommendationReason reason) {
        return switch (reason.code()) {
            case SAME_PROGRAM -> "Cùng chương trình học";
            case SAME_SPECIALIZATION -> "Cùng chuyên ngành với mentee";
            case SAME_CAMPUS -> "Cùng campus";
            case MENTOR_ALUMNI -> "Mentor là cựu sinh viên";
            case HIGHER_SEMESTER -> "Mentor đi trước mentee về học kỳ";
            case SAME_SEMESTER -> "Mentor cùng học kỳ chuyên ngành với mentee";
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
            case ALUMNI_OJT_FIT -> "Khớp nhu cầu góc nhìn alumni/OJT";
            case SIMILAR_MENTORING_EXPERIENCE -> "Mentor đã có trải nghiệm mentoring thực tế";
            case DECLARED_NEEDS_MATCH -> "Khớp nhu cầu mentoring đã khai báo";
            case COMPLETED_SESSION -> "Đã có phiên mentoring hoàn thành";
            case DEFAULT_DISCOVERY_MATCH -> "Phù hợp với các tiêu chí discovery hiện tại";
        };
    }
}
