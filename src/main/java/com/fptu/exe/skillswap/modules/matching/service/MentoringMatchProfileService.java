package com.fptu.exe.skillswap.modules.matching.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.matching.domain.*;
import com.fptu.exe.skillswap.modules.matching.dto.request.AdminQuestionnaireActivateRequest;
import com.fptu.exe.skillswap.modules.matching.dto.request.AdminQuestionnaireVersionCreateRequest;
import com.fptu.exe.skillswap.modules.matching.dto.request.MentoringMatchProfileSubmitRequest;
import com.fptu.exe.skillswap.modules.matching.dto.response.*;
import com.fptu.exe.skillswap.modules.matching.repository.*;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MentoringMatchProfileService {

    private static final List<String> QUESTION_ORDER = List.of(
            MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL,
            MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL,
            MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL,
            MentoringQuestionnaireDefaults.Q4_MENTOR_FIT,
            MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE
    );

    private final MentoringQuestionnaireVersionRepository versionRepository;
    private final MentoringQuestionnaireQuestionRepository questionRepository;
    private final MentoringQuestionnaireOptionRepository optionRepository;
    private final MentoringQuestionnaireActivationRepository activationRepository;
    private final MentoringQuestionnaireAnswerRepository answerRepository;
    private final UserRepository userRepository;

    @Transactional
    public MentoringQuestionnaireResponse getActiveQuestionnaire() {
        MentoringQuestionnaireActivation activation = getOrCreateActiveActivationReadOnlySafe();
        return toQuestionnaireResponse(activation);
    }

    @Transactional
    public MentoringMatchProfileResponse getMyMatchingProfile(UUID userId) {
        requireUserId(userId);
        MentoringQuestionnaireActivation activation = getOrCreateActiveActivationReadOnlySafe();
        List<MentoringQuestionnaireAnswer> activeAnswers = answerRepository.findByActivationIdAndUserId(activation.getId(), userId);
        boolean completed = activeAnswers.size() == QUESTION_ORDER.size();
        MenteeMatchingFeatures features = latestFeatures(userId);
        LocalDateTime latestAnsweredAt = activeAnswers.stream()
                .map(MentoringQuestionnaireAnswer::getAnsweredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        Map<String, String> answerCodes = activeAnswers.stream()
                .collect(Collectors.toMap(MentoringQuestionnaireAnswer::getQuestionCode, MentoringQuestionnaireAnswer::getOptionCode, (left, right) -> right, LinkedHashMap::new));
        return MentoringMatchProfileResponse.builder()
                .exists(completed || features.hasAnySignal())
                .currentActivationCompleted(completed)
                .latestAnsweredAt(latestAnsweredAt)
                .activeActivationId(activation.getId())
                .activeVersionId(activation.getVersion().getId())
                .activeVersionNumber(activation.getVersion().getVersionNumber())
                .foundationNeedLevel(features.foundationNeedLevel())
                .outputReviewNeedLevel(features.outputReviewNeedLevel())
                .directionNeedLevel(features.directionNeedLevel())
                .mentorFitCode(features.mentorFitCode())
                .durationPreferenceCode(features.durationPreferenceCode())
                .latestAnswerCodes(answerCodes)
                .build();
    }

    @Transactional
    public MentoringMatchProfileResponse submitMyMatchingProfile(UUID userId, MentoringMatchProfileSubmitRequest request) {
        requireUserId(userId);
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu câu trả lời không được để trống");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        MentoringQuestionnaireActivation activation = getOrCreateActiveActivation();
        List<MentoringQuestionnaireQuestion> questions = questionRepository.findByVersionIdOrderByDisplayOrderAsc(activation.getVersion().getId());
        Map<String, MentoringQuestionnaireQuestion> questionsByCode = questions.stream()
                .collect(Collectors.toMap(MentoringQuestionnaireQuestion::getQuestionCode, Function.identity()));
        Map<String, String> submitted = submittedAnswers(request);
        List<MentoringQuestionnaireOption> options = optionRepository.findByQuestionIdInOrderByQuestionDisplayOrderAscDisplayOrderAsc(
                questions.stream().map(MentoringQuestionnaireQuestion::getId).toList()
        );
        Map<String, Map<String, MentoringQuestionnaireOption>> optionsByQuestionCode = options.stream()
                .collect(Collectors.groupingBy(option -> option.getQuestion().getQuestionCode(),
                        Collectors.toMap(MentoringQuestionnaireOption::getOptionCode, Function.identity())));

        answerRepository.deleteByActivationIdAndUserId(activation.getId(), userId);
        LocalDateTime now = DateTimeUtil.now();
        List<MentoringQuestionnaireAnswer> answers = new ArrayList<>();
        for (String questionCode : QUESTION_ORDER) {
            MentoringQuestionnaireQuestion question = questionsByCode.get(questionCode);
            if (question == null) {
                throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Questionnaire active đang thiếu câu hỏi " + questionCode);
            }
            String optionCode = clean(submitted.get(questionCode), "Câu trả lời " + questionCode);
            MentoringQuestionnaireOption option = Optional.ofNullable(optionsByQuestionCode.getOrDefault(questionCode, Map.of()).get(optionCode))
                    .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Mã câu trả lời không hợp lệ: " + optionCode));
            answers.add(MentoringQuestionnaireAnswer.builder()
                    .activation(activation)
                    .version(activation.getVersion())
                    .user(user)
                    .questionCode(questionCode)
                    .optionCode(option.getOptionCode())
                    .scoreValue(option.getScoreValue())
                    .answeredAt(now)
                    .build());
        }
        answerRepository.saveAll(answers);
        return getMyMatchingProfile(userId);
    }

    @Transactional(readOnly = true)
    public MenteeMatchingFeatures latestFeatures(UUID userId) {
        requireUserId(userId);
        MentoringQuestionnaireActivation activation = activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc().orElse(null);
        List<MentoringQuestionnaireAnswer> answers = activation == null
                ? answerRepository.findFirst5ByUserIdOrderByAnsweredAtDesc(userId)
                : answerRepository.findByActivationIdAndUserId(activation.getId(), userId);
        Map<String, MentoringQuestionnaireAnswer> byCode = answers.stream()
                .collect(Collectors.toMap(MentoringQuestionnaireAnswer::getQuestionCode, Function.identity(), (left, right) -> right));
        return new MenteeMatchingFeatures(
                score(byCode, MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL),
                score(byCode, MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL),
                score(byCode, MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL),
                option(byCode, MentoringQuestionnaireDefaults.Q4_MENTOR_FIT),
                option(byCode, MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE)
        );
    }

    @Transactional(readOnly = true)
    public List<AdminQuestionnaireVersionSummaryResponse> listVersions() {
        return versionRepository.findAllByOrderByVersionNumberDesc().stream()
                .map(this::toVersionSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentoringQuestionnaireResponse getVersion(UUID versionId) {
        MentoringQuestionnaireVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy questionnaire version"));
        return toQuestionnaireResponse(version, null);
    }

    @Transactional
    public MentoringQuestionnaireResponse createVersion(AdminQuestionnaireVersionCreateRequest request) {
        int nextVersion = versionRepository.maxVersionNumber() + 1;
        MentoringQuestionnaireVersion version = versionRepository.save(MentoringQuestionnaireVersion.builder()
                .versionNumber(nextVersion)
                .active(false)
                .build());
        createQuestions(version, normalizeAdminQuestions(request));
        return toQuestionnaireResponse(version, null);
    }

    @Transactional
    public AdminQuestionnaireActivationResponse activateVersion(UUID adminUserId, AdminQuestionnaireActivateRequest request) {
        if (request == null || request.versionId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "versionId không được để trống");
        }
        MentoringQuestionnaireVersion version = versionRepository.findById(request.versionId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy questionnaire version"));
        validateVersionComplete(version);
        User admin = adminUserId == null ? null : userRepository.findById(adminUserId).orElse(null);
        activationRepository.findActiveForUpdate().ifPresent(active -> {
            active.setDeactivatedAt(DateTimeUtil.now());
            active.getVersion().setActive(false);
        });
        versionRepository.findAll().forEach(existing -> {
            if (existing.isActive()) {
                existing.setActive(false);
            }
        });
        version.setActive(true);
        MentoringQuestionnaireActivation activation = activationRepository.save(MentoringQuestionnaireActivation.builder()
                .version(version)
                .activatedBy(admin)
                .activatedAt(DateTimeUtil.now())
                .build());
        return toActivationResponse(activation);
    }

    @Transactional(readOnly = true)
    public AdminQuestionnaireActivationResponse getActiveActivation() {
        return activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc()
                .map(this::toActivationResponse)
                .orElse(null);
    }

    public boolean hasCompletedCurrentActivation(UUID userId) {
        if (userId == null) {
            return false;
        }
        return activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc()
                .map(active -> answerRepository.countByActivationIdAndUserId(active.getId(), userId) == QUESTION_ORDER.size())
                .orElse(false);
    }

    private MentoringQuestionnaireActivation getOrCreateActiveActivationReadOnlySafe() {
        return activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc()
                .orElseGet(this::getOrCreateActiveActivation);
    }

    @Transactional
    public MentoringQuestionnaireActivation getOrCreateActiveActivation() {
        return activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc()
                .orElseGet(() -> {
                    MentoringQuestionnaireVersion version = versionRepository.findFirstByActiveTrueOrderByVersionNumberDesc()
                            .orElseGet(this::createDefaultVersion);
                    version.setActive(true);
                    return activationRepository.save(MentoringQuestionnaireActivation.builder()
                            .version(version)
                            .activatedAt(DateTimeUtil.now())
                            .build());
                });
    }

    private MentoringQuestionnaireVersion createDefaultVersion() {
        int nextVersion = versionRepository.maxVersionNumber() + 1;
        MentoringQuestionnaireVersion version = versionRepository.save(MentoringQuestionnaireVersion.builder()
                .versionNumber(nextVersion)
                .active(true)
                .build());
        createQuestions(version, MentoringQuestionnaireDefaults.questions().stream()
                .map(defaultQuestion -> new AdminQuestionnaireVersionCreateRequest.QuestionRequest(
                        defaultQuestion.code(),
                        defaultQuestion.type(),
                        defaultQuestion.text(),
                        defaultQuestion.options().stream()
                                .map(defaultOption -> new AdminQuestionnaireVersionCreateRequest.OptionRequest(
                                        defaultOption.code(),
                                        defaultOption.label(),
                                        defaultOption.scoreValue()
                                ))
                                .toList()
                ))
                .toList());
        return version;
    }

    private void createQuestions(MentoringQuestionnaireVersion version, List<AdminQuestionnaireVersionCreateRequest.QuestionRequest> questionRequests) {
        validateQuestionRequests(questionRequests);
        int questionOrder = 1;
        for (AdminQuestionnaireVersionCreateRequest.QuestionRequest questionRequest : questionRequests) {
            MentoringQuestionnaireQuestion question = questionRepository.save(MentoringQuestionnaireQuestion.builder()
                    .version(version)
                    .questionCode(clean(questionRequest.code(), "Mã câu hỏi"))
                    .questionType(questionRequest.type())
                    .questionText(clean(questionRequest.questionText(), "Nội dung câu hỏi"))
                    .displayOrder(questionOrder++)
                    .build());
            int optionOrder = 1;
            for (AdminQuestionnaireVersionCreateRequest.OptionRequest optionRequest : questionRequest.options()) {
                optionRepository.save(MentoringQuestionnaireOption.builder()
                        .question(question)
                        .optionCode(clean(optionRequest.code(), "Mã option"))
                        .optionLabel(clean(optionRequest.label(), "Label option"))
                        .scoreValue(optionRequest.scoreValue())
                        .displayOrder(optionOrder++)
                        .build());
            }
        }
        validateVersionComplete(version);
    }

    private void validateQuestionRequests(List<AdminQuestionnaireVersionCreateRequest.QuestionRequest> questionRequests) {
        if (questionRequests == null || questionRequests.size() != QUESTION_ORDER.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Questionnaire version phải có đúng 5 câu hỏi");
        }
        Set<String> questionCodes = new HashSet<>();
        for (AdminQuestionnaireVersionCreateRequest.QuestionRequest questionRequest : questionRequests) {
            if (questionRequest == null) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Questionnaire version có câu hỏi không hợp lệ");
            }
            String questionCode = clean(questionRequest.code(), "Mã câu hỏi");
            if (!QUESTION_ORDER.contains(questionCode) || !questionCodes.add(questionCode)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Questionnaire version thiếu hoặc trùng semantic question code bắt buộc");
            }
            MentoringQuestionType expectedType = expectedQuestionType(questionCode);
            if (questionRequest.type() == null || questionRequest.type() != expectedType) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Loại câu hỏi không hợp lệ cho " + questionCode);
            }
            clean(questionRequest.questionText(), "Nội dung câu hỏi");
            List<AdminQuestionnaireVersionCreateRequest.OptionRequest> options = questionRequest.options();
            if (options == null || options.size() != expectedOptionCount(expectedType)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Số lượng option không hợp lệ cho " + questionCode);
            }
            Set<String> optionCodes = new HashSet<>();
            for (AdminQuestionnaireVersionCreateRequest.OptionRequest optionRequest : options) {
                if (optionRequest == null) {
                    throw new BaseException(ErrorCode.BAD_REQUEST, "Questionnaire version có option không hợp lệ");
                }
                String optionCode = clean(optionRequest.code(), "Mã option");
                clean(optionRequest.label(), "Label option");
                if (!optionCodes.add(optionCode)) {
                    throw new BaseException(ErrorCode.BAD_REQUEST, "Mã option bị trùng trong " + questionCode);
                }
            }
        }
    }

    private List<AdminQuestionnaireVersionCreateRequest.QuestionRequest> normalizeAdminQuestions(AdminQuestionnaireVersionCreateRequest request) {
        if (request == null || request.questions() == null || request.questions().isEmpty()) {
            return MentoringQuestionnaireDefaults.questions().stream()
                    .map(defaultQuestion -> new AdminQuestionnaireVersionCreateRequest.QuestionRequest(
                            defaultQuestion.code(),
                            defaultQuestion.type(),
                            defaultQuestion.text(),
                            defaultQuestion.options().stream()
                                    .map(defaultOption -> new AdminQuestionnaireVersionCreateRequest.OptionRequest(defaultOption.code(), defaultOption.label(), defaultOption.scoreValue()))
                                    .toList()
                    ))
                    .toList();
        }
        return request.questions();
    }

    private void validateVersionComplete(MentoringQuestionnaireVersion version) {
        List<MentoringQuestionnaireQuestion> questions = questionRepository.findByVersionIdOrderByDisplayOrderAsc(version.getId());
        if (questions.size() != QUESTION_ORDER.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Questionnaire version phải có đúng 5 câu hỏi");
        }
        Set<String> codes = questions.stream().map(MentoringQuestionnaireQuestion::getQuestionCode).collect(Collectors.toSet());
        if (!codes.containsAll(QUESTION_ORDER)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Questionnaire version thiếu semantic question code bắt buộc");
        }
        List<MentoringQuestionnaireOption> options = optionRepository.findByQuestionIdInOrderByQuestionDisplayOrderAscDisplayOrderAsc(
                questions.stream().map(MentoringQuestionnaireQuestion::getId).toList()
        );
        Map<UUID, List<MentoringQuestionnaireOption>> optionsByQuestionId = options.stream()
                .collect(Collectors.groupingBy(option -> option.getQuestion().getId()));
        for (MentoringQuestionnaireQuestion question : questions) {
            MentoringQuestionType expectedType = expectedQuestionType(question.getQuestionCode());
            if (question.getQuestionType() == null || question.getQuestionType() != expectedType) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Loại câu hỏi không hợp lệ cho " + question.getQuestionCode());
            }
            List<MentoringQuestionnaireOption> questionOptions = optionsByQuestionId.getOrDefault(question.getId(), List.of());
            if (questionOptions.size() != expectedOptionCount(expectedType)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Số lượng option không hợp lệ cho " + question.getQuestionCode());
            }
            Set<String> optionCodes = new HashSet<>();
            for (MentoringQuestionnaireOption option : questionOptions) {
                if (!StringUtils.hasText(option.getOptionCode()) || !StringUtils.hasText(option.getOptionLabel()) || !optionCodes.add(option.getOptionCode())) {
                    throw new BaseException(ErrorCode.BAD_REQUEST, "Option không hợp lệ cho " + question.getQuestionCode());
                }
            }
        }
    }

    private MentoringQuestionType expectedQuestionType(String questionCode) {
        return switch (questionCode) {
            case MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL,
                 MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL,
                 MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL -> MentoringQuestionType.LEVEL;
            case MentoringQuestionnaireDefaults.Q4_MENTOR_FIT -> MentoringQuestionType.FIT;
            case MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE -> MentoringQuestionType.DURATION_PREFERENCE;
            default -> throw new BaseException(ErrorCode.BAD_REQUEST, "Semantic question code không hợp lệ: " + questionCode);
        };
    }

    private int expectedOptionCount(MentoringQuestionType type) {
        return switch (type) {
            case LEVEL, DURATION_PREFERENCE -> 4;
            case FIT -> 3;
        };
    }

    private MentoringQuestionnaireResponse toQuestionnaireResponse(MentoringQuestionnaireActivation activation) {
        return toQuestionnaireResponse(activation.getVersion(), activation);
    }

    private MentoringQuestionnaireResponse toQuestionnaireResponse(MentoringQuestionnaireVersion version, MentoringQuestionnaireActivation activation) {
        List<MentoringQuestionnaireQuestion> questions = questionRepository.findByVersionIdOrderByDisplayOrderAsc(version.getId());
        List<MentoringQuestionnaireOption> options = optionRepository.findByQuestionIdInOrderByQuestionDisplayOrderAscDisplayOrderAsc(
                questions.stream().map(MentoringQuestionnaireQuestion::getId).toList()
        );
        Map<UUID, List<MentoringQuestionnaireOption>> optionsByQuestionId = options.stream()
                .collect(Collectors.groupingBy(option -> option.getQuestion().getId(), LinkedHashMap::new, Collectors.toList()));
        return MentoringQuestionnaireResponse.builder()
                .activationId(activation == null ? null : activation.getId())
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .phase("ACTIVE")
                .activatedAt(activation == null ? null : activation.getActivatedAt())
                .questions(questions.stream()
                        .map(question -> MentoringQuestionnaireQuestionResponse.builder()
                                .code(question.getQuestionCode())
                                .type(question.getQuestionType())
                                .questionText(question.getQuestionText())
                                .displayOrder(question.getDisplayOrder())
                                .options(optionsByQuestionId.getOrDefault(question.getId(), List.of()).stream()
                                        .map(option -> MentoringQuestionnaireOptionResponse.builder()
                                                .code(option.getOptionCode())
                                                .label(option.getOptionLabel())
                                                .scoreValue(option.getScoreValue())
                                                .displayOrder(option.getDisplayOrder())
                                                .build())
                                        .toList())
                                .build())
                        .toList())
                .build();
    }

    private AdminQuestionnaireVersionSummaryResponse toVersionSummary(MentoringQuestionnaireVersion version) {
        return AdminQuestionnaireVersionSummaryResponse.builder()
                .id(version.getId())
                .versionNumber(version.getVersionNumber())
                .active(version.isActive())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .build();
    }

    private AdminQuestionnaireActivationResponse toActivationResponse(MentoringQuestionnaireActivation activation) {
        return AdminQuestionnaireActivationResponse.builder()
                .activationId(activation.getId())
                .versionId(activation.getVersion().getId())
                .versionNumber(activation.getVersion().getVersionNumber())
                .activatedAt(activation.getActivatedAt())
                .deactivatedAt(activation.getDeactivatedAt())
                .build();
    }

    private Map<String, String> submittedAnswers(MentoringMatchProfileSubmitRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL, request.question1AnswerCode());
        result.put(MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL, request.question2AnswerCode());
        result.put(MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL, request.question3AnswerCode());
        result.put(MentoringQuestionnaireDefaults.Q4_MENTOR_FIT, request.question4AnswerCode());
        result.put(MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE, request.question5AnswerCode());
        return result;
    }

    private Integer score(Map<String, MentoringQuestionnaireAnswer> answers, String code) {
        MentoringQuestionnaireAnswer answer = answers.get(code);
        return answer == null ? null : answer.getScoreValue();
    }

    private String option(Map<String, MentoringQuestionnaireAnswer> answers, String code) {
        MentoringQuestionnaireAnswer answer = answers.get(code);
        return answer == null ? null : answer.getOptionCode();
    }

    private String clean(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, label + " không được để trống");
        }
        return value.trim();
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
