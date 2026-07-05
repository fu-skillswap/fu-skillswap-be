package com.fptu.exe.skillswap.modules.matching.service;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionType;

import java.util.List;

final class MentoringQuestionnaireDefaults {

    static final String Q1_FOUNDATION_LEVEL = "Q1_FOUNDATION_LEVEL";
    static final String Q2_OUTPUT_REVIEW_LEVEL = "Q2_OUTPUT_REVIEW_LEVEL";
    static final String Q3_DIRECTION_LEVEL = "Q3_DIRECTION_LEVEL";
    static final String Q4_MENTOR_FIT = "Q4_MENTOR_FIT";
    static final String Q5_DURATION_PREFERENCE = "Q5_DURATION_PREFERENCE";

    private MentoringQuestionnaireDefaults() {
    }

    static List<DefaultQuestion> questions() {
        return List.of(
                new DefaultQuestion(
                        Q1_FOUNDATION_LEVEL,
                        MentoringQuestionType.LEVEL,
                        "Khi gặp chỗ chưa hiểu, bạn thường cần mentor hỗ trợ tới mức nào?",
                        List.of(
                                new DefaultOption("FOUNDATION_1", "Mình thường tự xem lại là đủ", 1),
                                new DefaultOption("FOUNDATION_2", "Mình cần ai đó giải thích lại vài ý chính", 2),
                                new DefaultOption("FOUNDATION_3", "Mình cần người gỡ rõ phần mình đang hổng", 3),
                                new DefaultOption("FOUNDATION_4", "Mình rất cần mentor giúp mình hiểu lại từ gốc", 4)
                        )
                ),
                new DefaultQuestion(
                        Q2_OUTPUT_REVIEW_LEVEL,
                        MentoringQuestionType.LEVEL,
                        "Khi có project, bài nộp, slide, CV hoặc sản phẩm cần góp ý, bạn thường cần mentor review tới mức nào?",
                        List.of(
                                new DefaultOption("OUTPUT_REVIEW_1", "Mình thường tự hoàn thiện được", 1),
                                new DefaultOption("OUTPUT_REVIEW_2", "Mình cần góp ý nhanh trước khi chốt", 2),
                                new DefaultOption("OUTPUT_REVIEW_3", "Mình cần review khá kỹ để biết phần nào chưa ổn", 3),
                                new DefaultOption("OUTPUT_REVIEW_4", "Mình rất cần mentor xem trực tiếp và chỉ rõ nên sửa gì", 4)
                        )
                ),
                new DefaultQuestion(
                        Q3_DIRECTION_LEVEL,
                        MentoringQuestionType.LEVEL,
                        "Khi đang phân vân giữa nhiều hướng làm, học hoặc chuẩn bị cho bước tiếp theo, bạn thường cần mentor tới mức nào?",
                        List.of(
                                new DefaultOption("DIRECTION_1", "Mình thường tự quyết được", 1),
                                new DefaultOption("DIRECTION_2", "Mình cần thêm một góc nhìn để yên tâm hơn", 2),
                                new DefaultOption("DIRECTION_3", "Mình khá cần người đi trước giúp mình gỡ rối", 3),
                                new DefaultOption("DIRECTION_4", "Mình rất cần mentor giúp mình chốt bước tiếp theo", 4)
                        )
                ),
                new DefaultQuestion(
                        Q4_MENTOR_FIT,
                        MentoringQuestionType.FIT,
                        "Nếu book một buổi mentor lúc đang kẹt, kiểu anh chị nào thường hợp với bạn nhất?",
                        List.of(
                                new DefaultOption("MENTOR_FIT_SUBJECT_MATCH", "Anh chị mạnh đúng môn hoặc đúng phần mình đang cần", null),
                                new DefaultOption("MENTOR_FIT_SIMILAR_EXPERIENCE", "Người từng gặp đúng kiểu vấn đề mình đang vướng", null),
                                new DefaultOption("MENTOR_FIT_RECENT_ALUMNI", "Alumni mới ra trường, gần với chuyện OJT, thực tập và đi làm", null)
                        )
                ),
                new DefaultQuestion(
                        Q5_DURATION_PREFERENCE,
                        MentoringQuestionType.DURATION_PREFERENCE,
                        "Nếu chỉ book một buổi để gỡ việc, bạn thường thấy thời lượng nào hợp mình nhất?",
                        List.of(
                                new DefaultOption("DURATION_15", "15 phút", 15),
                                new DefaultOption("DURATION_30", "30 phút", 30),
                                new DefaultOption("DURATION_60", "60 phút", 60),
                                new DefaultOption("DURATION_90", "90 phút", 90)
                        )
                )
        );
    }

    record DefaultQuestion(String code, MentoringQuestionType type, String text, List<DefaultOption> options) {
    }

    record DefaultOption(String code, String label, Integer scoreValue) {
    }
}
