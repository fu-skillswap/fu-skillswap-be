package com.fptu.exe.skillswap.modules.demo.seeder;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.GenderCode;
import com.fptu.exe.skillswap.modules.identity.domain.OauthAccount;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.OauthAccountRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequestEvent;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class DevDemoDataSeeder implements CommandLineRunner {

    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final int ACTIVE_MENTOR_COUNT = 5;
    private static final int PENDING_MENTOR_COUNT = 5;
    private static final int MENTEE_COUNT = 10;
    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final Set<VerificationStatus> OPEN_REQUEST_STATUSES = EnumSet.of(
            VerificationStatus.DRAFT,
            VerificationStatus.PENDING_REVIEW,
            VerificationStatus.NEEDS_REVISION
    );

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final CampusRepository campusRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final SpecializationRepository specializationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final StoredFileRepository storedFileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final TagRepository tagRepository;
    private final MentorTagRepository mentorTagRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting SkillSwap demo data seeding...");

        Map<String, Tag> helpTopics = loadHelpTopics();
        seedMentees();
        seedMentors(helpTopics);

        log.info("SkillSwap demo data seeding completed successfully!");
    }

    private void seedMentees() {
        for (StudentSeed seed : menteeSeeds()) {
            User user = upsertUser(seed.email(), seed.fullName(), seed.avatarUrl(), Set.of(RoleCode.MENTEE));
            upsertOauthAccount(user, demoProviderUserId(seed.email()));
            upsertStudentProfile(user, seed);
        }
    }

    private void seedMentors(Map<String, Tag> helpTopics) {
        for (MentorSeed seed : mentorSeeds()) {
            User user = upsertUser(seed.email(), seed.fullName(), seed.avatarUrl(), Set.of(RoleCode.MENTEE, RoleCode.MENTOR));
            upsertOauthAccount(user, demoProviderUserId(seed.email()));
            upsertStudentProfile(user, seed.toStudentSeed());

            MentorProfile mentorProfile = upsertMentorProfile(user, seed);
            upsertMentorTags(mentorProfile, seed.helpTopicCodes(), helpTopics);

            if (seed.activeMentor()) {
                upsertMentorService(mentorProfile, seed, helpTopics);
                upsertAvailabilityPlan(mentorProfile);
                continue;
            }

            upsertPendingVerificationRequest(user, mentorProfile, seed, helpTopics);
        }
    }

    private User upsertUser(String email, String fullName, String avatarUrl, Set<RoleCode> roles) {
        User user = userRepository.findByEmailIncludingDeleted(email)
                .orElseGet(User::new);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setAvatarUrl(avatarUrl);
        user.setPhone(null);
        user.setGender(null);
        user.setDateOfBirth(null);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(DateTimeUtil.now().minusDays(1));
        user.setDeletedAt(null);
        user.setRoles(new HashSet<>(roles));
        return userRepository.save(user);
    }

    private void upsertOauthAccount(User user, String providerUserId) {
        OauthAccount oauthAccount = oauthAccountRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, providerUserId)
                .orElseGet(OauthAccount::new);
        oauthAccount.setUser(user);
        oauthAccount.setProvider(GOOGLE_PROVIDER);
        oauthAccount.setProviderUserId(providerUserId);
        oauthAccount.setProviderEmail(user.getEmail());
        oauthAccountRepository.save(oauthAccount);
    }

    private StudentProfile upsertStudentProfile(User user, StudentSeed seed) {
        Campus campus = campus(seed.campusCode());
        AcademicProgram program = academicProgram(seed.programCode());
        Specialization specialization = specialization(seed.specializationCode());

        StudentProfile profile = studentProfileRepository.findWithDetailsByUserId(user.getId())
                .orElseGet(StudentProfile::new);
        profile.setUser(user);
        profile.setStudentCode(seed.studentCode());
        profile.setCampus(campus);
        profile.setProgram(program);
        profile.setSpecialization(specialization);
        profile.setSemester(seed.semester());
        profile.setIntakeYear(seed.intakeYear());
        profile.setAlumni(seed.alumni());
        profile.setGraduationYear(seed.graduationYear());
        profile.setBio(seed.bio());
        return studentProfileRepository.save(profile);
    }

    private MentorProfile upsertMentorProfile(User user, MentorSeed seed) {
        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(user.getId())
                .orElseGet(MentorProfile::new);
        mentorProfile.setUser(user);
        mentorProfile.setStatus(seed.activeMentor() ? MentorStatus.ACTIVE : MentorStatus.PENDING_VERIFICATION);
        mentorProfile.setHeadline(seed.headline());
        mentorProfile.setExpertiseDescription(seed.expertiseDescription());
        mentorProfile.setSupportingSubjects(seed.supportingSubjects());
        mentorProfile.setTeachingMode(seed.teachingMode());
        mentorProfile.setSessionDuration(seed.sessionDuration());
        mentorProfile.setPortfolioUrl(null);
        mentorProfile.setLinkedinUrl(null);
        mentorProfile.setGithubUrl(null);
        mentorProfile.setAverageRating(seed.averageRating());
        mentorProfile.setTotalReviews(seed.activeMentor() ? 18 : 0);
        mentorProfile.setTotalSessions(seed.activeMentor() ? 20 : 0);
        mentorProfile.setTotalCompletedSessions(seed.activeMentor() ? 18 : 0);
        mentorProfile.setTotalRejectedBookings(seed.activeMentor() ? 1 : 0);
        mentorProfile.setLateCancellationPenaltyPoints(seed.activeMentor() ? BigDecimal.valueOf(0.2) : BigDecimal.ZERO);
        mentorProfile.setAvailable(seed.activeMentor());
        mentorProfile.setBookingSuspendedUntil(null);
        mentorProfile.setVerifiedAt(seed.activeMentor() ? DateTimeUtil.now().minusDays(seed.verifiedDaysAgo()) : null);
        mentorProfile.setVerifiedBy(null);
        return mentorProfileRepository.save(mentorProfile);
    }

    private void upsertMentorTags(MentorProfile mentorProfile, List<String> tagCodes, Map<String, Tag> tags) {
        if (mentorProfile == null || mentorProfile.getUserId() == null || tagCodes == null || tagCodes.isEmpty()) {
            return;
        }

        Set<UUID> existingTagIds = mentorTagRepository.findByIdMentorUserIdAndIdTagTypeIn(
                        mentorProfile.getUserId(),
                        List.of(MentorTagType.HELP_TOPIC)
                ).stream()
                .map(item -> item.getId().getTagId())
                .collect(Collectors.toSet());

        boolean primaryAssigned = false;
        for (String code : tagCodes) {
            Tag tag = tags.get(code);
            if (tag == null || existingTagIds.contains(tag.getId())) {
                continue;
            }

            MentorTag mentorTag = MentorTag.builder()
                    .id(new MentorTagId(mentorProfile.getUserId(), tag.getId(), MentorTagType.HELP_TOPIC))
                    .mentorProfile(mentorProfile)
                    .tag(tag)
                    .isPrimary(!primaryAssigned)
                    .build();
            mentorTagRepository.save(mentorTag);
            primaryAssigned = true;
        }
    }

    private void upsertMentorService(MentorProfile mentorProfile, MentorSeed seed, Map<String, Tag> helpTopics) {
        List<MentorService> existing = mentorServiceRepository.findByMentorProfileUserIdOrderByCreatedAtAsc(mentorProfile.getUserId());
        if (!existing.isEmpty()) {
            MentorService service = existing.get(0);
            service.setMentorProfile(mentorProfile);
            service.setTitle(seed.serviceTitle());
            service.setDescription(seed.serviceDescription());
            service.setDurationMinutes(seed.serviceDuration());
            service.setFree(seed.serviceFree());
            service.setPriceAmount(seed.priceAmount());
            service.setCurrency("VND");
            service.setActive(true);
            service.getHelpTopics().clear();
            for (String code : seed.helpTopicCodes()) {
                Tag tag = helpTopics.get(code);
                if (tag != null) {
                    service.getHelpTopics().add(tag);
                }
            }
            mentorServiceRepository.save(service);
            return;
        }

        MentorService service = MentorService.builder()
                .mentorProfile(mentorProfile)
                .title(seed.serviceTitle())
                .description(seed.serviceDescription())
                .durationMinutes(seed.serviceDuration())
                .isFree(seed.serviceFree())
                .priceAmount(seed.priceAmount())
                .currency("VND")
                .isActive(true)
                .build();
        for (String code : seed.helpTopicCodes()) {
            Tag tag = helpTopics.get(code);
            if (tag != null) {
                service.getHelpTopics().add(tag);
            }
        }
        mentorServiceRepository.save(service);
    }

    private void upsertAvailabilityPlan(MentorProfile mentorProfile) {
        UUID mentorUserId = mentorProfile.getUserId();
        List<MentorAvailabilityRule> rules = mentorAvailabilityRuleRepository.findByMentorProfileUserIdAndActiveTrueOrderByEffectiveFromAscStartTimeAsc(mentorUserId);
        if (rules.isEmpty()) {
            MentorAvailabilityRule rule = MentorAvailabilityRule.builder()
                    .mentorProfile(mentorProfile)
                    .ruleType(AvailabilityRuleType.OPEN)
                    .repeatType(AvailabilityRepeatType.WEEKLY)
                    .daysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                    .effectiveFrom(DateTimeUtil.now().toLocalDate())
                    .effectiveTo(DateTimeUtil.now().toLocalDate().plusMonths(3))
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .timezone(DEFAULT_TIMEZONE)
                    .active(true)
                    .note("Demo availability plan")
                    .build();
            mentorAvailabilityRuleRepository.save(rule);
        }

        List<SlotSeed> slots = List.of(
                new SlotSeed(1, 9, 0, mentorProfile.getSessionDuration()),
                new SlotSeed(1, 14, 0, mentorProfile.getSessionDuration()),
                new SlotSeed(2, 9, 0, mentorProfile.getSessionDuration())
        );
        for (SlotSeed slotSeed : slots) {
            LocalDateTime start = DateTimeUtil.now().toLocalDate().plusDays(slotSeed.dayOffset()).atTime(slotSeed.hour(), slotSeed.minute());
            LocalDateTime end = start.plusMinutes(slotSeed.durationMinutes());
            boolean exists = mentorAvailabilitySlotRepository.existsByMentorProfileUserIdAndStartTimeAndEndTimeAndIsActiveTrue(
                    mentorUserId,
                    start,
                    end
            );
            if (exists) {
                continue;
            }

            MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .startTime(start)
                    .endTime(end)
                    .timezone(DEFAULT_TIMEZONE)
                    .isBooked(false)
                    .isActive(true)
                    .build();
            mentorAvailabilitySlotRepository.save(slot);
        }
    }

    private void upsertPendingVerificationRequest(User user, MentorProfile mentorProfile, MentorSeed seed, Map<String, Tag> helpTopics) {
        MentorVerificationRequest request = mentorVerificationRequestRepository
                .findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(user.getId(), OPEN_REQUEST_STATUSES)
                .orElseGet(MentorVerificationRequest::new);

        request.setMentor(user);
        request.setMethod(com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod.MANUAL);
        request.setStatus(VerificationStatus.PENDING_REVIEW);
        request.setRevisionCount(0);
        request.setSubmittedNote("Hồ sơ demo chờ duyệt");
        request.setReviewNote(null);
        request.setTermsAcceptedAt(DateTimeUtil.now().minusDays(1));
        request.setTermsVersion("SKILLSWAP_MENTOR_TERMS_V1");
        request.setSubmittedAt(DateTimeUtil.now().minusHours(4));
        request.setReviewedBy(null);
        request.setReviewedAt(null);
        request.setWithdrawnAt(null);
        request.setApprovedAt(null);
        request.setRejectionReason(null);
        request.setLockedBy(null);
        request.setLockedAt(null);
        request.setLockExpiresAt(null);
        request.setPreviousRequest(null);
        MentorVerificationRequest savedRequest = mentorVerificationRequestRepository.save(request);

        mentorProfile.setStatus(MentorStatus.PENDING_VERIFICATION);
        mentorProfile.setAvailable(false);
        mentorProfile.setVerifiedAt(null);
        mentorProfile.setBookingSuspendedUntil(null);
        mentorProfileRepository.save(mentorProfile);

        if (mentorVerificationRequestDocumentCount(savedRequest.getId()) == 0) {
            createVerificationDocument(savedRequest, user, VerificationDocumentType.FPTU_AFFILIATION_PROOF, "jpg");
            createVerificationDocument(savedRequest, user, VerificationDocumentType.EXPERTISE_PROOF, "pdf");
        }

        if (mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(savedRequest.getId()).isEmpty()) {
            appendRequestEvent(savedRequest, MentorVerificationEventType.REQUEST_CREATED, null, VerificationStatus.DRAFT, VerificationStatus.DRAFT, "Tạo hồ sơ demo");
            appendRequestEvent(savedRequest, MentorVerificationEventType.SUBMITTED, user, VerificationStatus.DRAFT, VerificationStatus.PENDING_REVIEW, "Nộp hồ sơ demo");
        }
    }

    private long mentorVerificationRequestDocumentCount(UUID requestId) {
        return mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(requestId).size();
    }

    private void createVerificationDocument(MentorVerificationRequest request, User user, VerificationDocumentType documentType, String extension) {
        StoredFile storedFile = StoredFile.builder()
                .owner(user)
                .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                .originalName(demoFileName(request.getMentor().getEmail(), documentType, extension))
                .storageProvider("DEMO")
                .storageKey("demo/verification/" + request.getMentor().getEmail() + "/" + documentType.name().toLowerCase() + "." + extension)
                .publicUrl("https://storage.skillswap.local/" + request.getMentor().getEmail() + "/" + documentType.name().toLowerCase() + "." + extension)
                .mimeType("pdf".equalsIgnoreCase(extension) ? "application/pdf" : "image/jpeg")
                .sizeBytes("pdf".equalsIgnoreCase(extension) ? 256_000L : 128_000L)
                .checksum("demo-" + request.getMentor().getEmail() + "-" + documentType.name())
                .build();
        StoredFile savedFile = storedFileRepository.save(storedFile);

        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .request(request)
                .documentType(documentType)
                .status(VerificationDocumentStatus.UPLOADED)
                .storageKind("pdf".equalsIgnoreCase(extension) ? VerificationStorageKind.DOCUMENT : VerificationStorageKind.IMAGE)
                .storedFile(savedFile)
                .isActive(true)
                .version(1)
                .uploadedBy(user)
                .build();
        mentorVerificationDocumentRepository.save(document);
    }

    private void appendRequestEvent(
            MentorVerificationRequest request,
            MentorVerificationEventType eventType,
            User actor,
            VerificationStatus fromStatus,
            VerificationStatus toStatus,
            String note
    ) {
        MentorVerificationRequestEvent event = MentorVerificationRequestEvent.builder()
                .request(request)
                .eventType(eventType)
                .actorUser(actor)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .note(note)
                .build();
        mentorVerificationRequestEventRepository.save(event);
    }

    private Tag getRequiredTag(Map<String, Tag> tags, String code) {
        Tag tag = tags.get(code);
        if (tag == null) {
            throw new IllegalStateException("Missing tag seed: " + code);
        }
        return tag;
    }

    private Map<String, Tag> loadHelpTopics() {
        List<String> codes = List.of(
                "HELP_CV_REVIEW",
                "HELP_INTERVIEW",
                "HELP_CAREER_PATH",
                "HELP_INTERNSHIP",
                "HELP_PROJECT_REVIEW",
                "HELP_GRADUATION_THESIS",
                "HELP_PRODUCT_FEEDBACK",
                "HELP_QA",
                "HELP_STUDY_PLAN"
        );
        Map<String, Tag> tags = new HashMap<>();
        for (String code : codes) {
            Tag tag = tagRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Missing help topic tag: " + code));
            if (tag.getStatus() != TagStatus.ACTIVE) {
                tag.setStatus(TagStatus.ACTIVE);
                tagRepository.save(tag);
            }
            tags.put(code, tag);
        }
        return tags;
    }

    private Campus campus(CampusCode code) {
        return campusRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing campus seed: " + code));
    }

    private AcademicProgram academicProgram(String code) {
        return academicProgramRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing academic program seed: " + code));
    }

    private Specialization specialization(String code) {
        return specializationRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing specialization seed: " + code));
    }

    private String demoProviderUserId(String email) {
        return "demo-google-" + email;
    }

    private String demoAvatarUrl(String key) {
        return "https://storage.skillswap.local/avatars/" + key + ".png";
    }

    private String demoFileName(String email, VerificationDocumentType documentType, String extension) {
        return email + "-" + documentType.name().toLowerCase() + "." + extension;
    }

    private List<MentorSeed> mentorSeeds() {
        return List.of(
                new MentorSeed(
                        "mentor01.demo@skillswap.local",
                        "Nguyen Van Minh",
                        "SE220001",
                        CampusCode.HCM,
                        "CNTT",
                        "CNTT_KTPM",
                        8,
                        2022,
                        true,
                        2025,
                        "Spring Boot Mentor | Backend Developer",
                        "Spring Boot, REST API, PostgreSQL, Docker, clean architecture, support EXE101 and EXE201.",
                        "EXE101, EXE201, PRJ301",
                        TeachingMode.ONLINE,
                        60,
                        demoAvatarUrl("mentor01"),
                        List.of("HELP_CV_REVIEW", "HELP_INTERVIEW", "HELP_QA"),
                        "Review CV Backend",
                        "Review CV for backend internship and junior backend roles.",
                        60,
                        false,
                        BigDecimal.valueOf(120000),
                        true,
                        14,
                        24,
                        18,
                        BigDecimal.valueOf(4.7)
                ),
                new MentorSeed(
                        "mentor02.demo@skillswap.local",
                        "Tran Thi Hoa",
                        "SE220002",
                        CampusCode.HA_NOI,
                        "CNTT",
                        "CNTT_TTNT",
                        9,
                        2021,
                        false,
                        null,
                        "AI Mentor | Machine Learning",
                        "Machine learning, Python, data processing, interview preparation, project guidance.",
                        "ML101, AI100, DSA",
                        TeachingMode.HYBRID,
                        90,
                        demoAvatarUrl("mentor02"),
                        List.of("HELP_PROJECT_REVIEW", "HELP_INTERNSHIP", "HELP_STUDY_PLAN"),
                        "Project Review AI",
                        "Review AI/ML project and suggest improvements for final presentations.",
                        90,
                        false,
                        BigDecimal.valueOf(180000),
                        true,
                        21,
                        30,
                        16,
                        BigDecimal.valueOf(4.8)
                ),
                new MentorSeed(
                        "mentor03.demo@skillswap.local",
                        "Le Quang Huy",
                        "SE220003",
                        CampusCode.DA_NANG,
                        "QTKD",
                        "QTKD_MKT",
                        7,
                        2020,
                        true,
                        2024,
                        "Career Mentor | Business Analyst",
                        "Career planning, CV improvement, interview practice, product thinking, team communication.",
                        "BUS101, COM102, EXE101",
                        TeachingMode.ONLINE,
                        60,
                        demoAvatarUrl("mentor03"),
                        List.of("HELP_CAREER_PATH", "HELP_CV_REVIEW", "HELP_INTERVIEW"),
                        "Career Guidance",
                        "Career orientation and internship preparation for business/tech students.",
                        60,
                        true,
                        BigDecimal.ZERO,
                        true,
                        18,
                        28,
                        20,
                        BigDecimal.valueOf(4.6)
                ),
                new MentorSeed(
                        "mentor04.demo@skillswap.local",
                        "Pham Duc Long",
                        "SE220004",
                        CampusCode.CAN_THO,
                        "CTTT",
                        "CTTT_TTDPM",
                        8,
                        2022,
                        false,
                        null,
                        "Frontend Mentor | UI/UX Review",
                        "Frontend architecture, UI/UX review, project storytelling, presentation tips.",
                        "WEB101, UIX201, PRJ301",
                        TeachingMode.HYBRID,
                        60,
                        demoAvatarUrl("mentor04"),
                        List.of("HELP_PROJECT_REVIEW", "HELP_PRODUCT_FEEDBACK", "HELP_QA"),
                        "UI Review Session",
                        "Review frontend screens, UX flow and presentation quality.",
                        60,
                        false,
                        BigDecimal.valueOf(100000),
                        true,
                        9,
                        14,
                        12,
                        BigDecimal.valueOf(4.4)
                ),
                new MentorSeed(
                        "mentor05.demo@skillswap.local",
                        "Doan Minh Chau",
                        "SE220005",
                        CampusCode.QUY_NHON,
                        "NN",
                        "NN_NNA",
                        8,
                        2021,
                        true,
                        2024,
                        "English Mentor | Interview Coach",
                        "English communication, interview answering, study plan, internship support.",
                        "ENG101, ENG201, COM102",
                        TeachingMode.ONLINE,
                        60,
                        demoAvatarUrl("mentor05"),
                        List.of("HELP_INTERVIEW", "HELP_CAREER_PATH", "HELP_STUDY_PLAN"),
                        "Interview Practice",
                        "Mock interview and feedback for English and communication skills.",
                        60,
                        false,
                        BigDecimal.valueOf(150000),
                        true,
                        11,
                        22,
                        19,
                        BigDecimal.valueOf(4.9)
                ),
                new MentorSeed(
                        "mentor06.demo@skillswap.local",
                        "Vo Anh Kiet",
                        "SE220006",
                        CampusCode.HCM,
                        "CNTT",
                        "CNTT_ATTT",
                        7,
                        2022,
                        false,
                        null,
                        "Security Mentor | Pending Verification",
                        "Information security basics, system hardening, secure coding, project review.",
                        "SEC101, EXE101, DSA",
                        TeachingMode.ONLINE,
                        60,
                        demoAvatarUrl("mentor06"),
                        List.of("HELP_CV_REVIEW", "HELP_QA"),
                        "Security Q&A",
                        "Demo profile waiting for verification approval.",
                        60,
                        true,
                        BigDecimal.valueOf(90000),
                        false,
                        null,
                        0,
                        0,
                        BigDecimal.ZERO
                ),
                new MentorSeed(
                        "mentor07.demo@skillswap.local",
                        "Ngo Phuong Anh",
                        "SE220007",
                        CampusCode.HA_NOI,
                        "CNTT",
                        "CNTT_HTTT",
                        6,
                        2023,
                        false,
                        null,
                        "System Analysis Mentor | Pending Verification",
                        "Requirements analysis, database design, UML, documentation support.",
                        "SE111, DB101, PRJ301",
                        TeachingMode.HYBRID,
                        60,
                        demoAvatarUrl("mentor07"),
                        List.of("HELP_PROJECT_REVIEW", "HELP_QA"),
                        "System Analysis Basics",
                        "Demo mentor profile awaiting approval.",
                        60,
                        true,
                        BigDecimal.valueOf(110000),
                        false,
                        null,
                        0,
                        0,
                        BigDecimal.ZERO
                ),
                new MentorSeed(
                        "mentor08.demo@skillswap.local",
                        "Bui Thanh Dat",
                        "SE220008",
                        CampusCode.DA_NANG,
                        "QTKD",
                        "QTKD_TMDT",
                        7,
                        2021,
                        false,
                        null,
                        "E-commerce Mentor | Pending Verification",
                        "E-commerce, business model, internship guidance, project review.",
                        "BUS101, MKT201, EXE101",
                        TeachingMode.ONLINE,
                        60,
                        demoAvatarUrl("mentor08"),
                        List.of("HELP_CAREER_PATH", "HELP_INTERNSHIP"),
                        "E-commerce Guidance",
                        "Demo mentor profile awaiting approval.",
                        60,
                        true,
                        BigDecimal.valueOf(100000),
                        false,
                        null,
                        0,
                        0,
                        BigDecimal.ZERO
                ),
                new MentorSeed(
                        "mentor09.demo@skillswap.local",
                        "Ngo Minh Tue",
                        "SE220009",
                        CampusCode.CAN_THO,
                        "CTTT",
                        "CTTT_QHCC",
                        9,
                        2020,
                        true,
                        2024,
                        "Communication Mentor | Pending Verification",
                        "Public speaking, presentation, teamwork, project delivery, career orientation.",
                        "COM101, COM102, PRJ301",
                        TeachingMode.OFFLINE,
                        90,
                        demoAvatarUrl("mentor09"),
                        List.of("HELP_INTERVIEW", "HELP_CAREER_PATH", "HELP_PRODUCT_FEEDBACK"),
                        "Presentation Coaching",
                        "Demo mentor profile awaiting approval.",
                        90,
                        false,
                        BigDecimal.valueOf(140000),
                        false,
                        null,
                        0,
                        0,
                        BigDecimal.ZERO
                ),
                new MentorSeed(
                        "mentor10.demo@skillswap.local",
                        "Luong Thi Mai",
                        "SE220010",
                        CampusCode.QUY_NHON,
                        "LUAT",
                        "LUAT_LKT",
                        8,
                        2023,
                        false,
                        null,
                        "Law Mentor | Pending Verification",
                        "Legal studies, presentation structure, career path, documentation support.",
                        "LAW101, EXE101, COM102",
                        TeachingMode.ONLINE,
                        60,
                        demoAvatarUrl("mentor10"),
                        List.of("HELP_QA", "HELP_STUDY_PLAN"),
                        "Law Study Support",
                        "Demo mentor profile awaiting approval.",
                        60,
                        true,
                        BigDecimal.valueOf(80000),
                        false,
                        null,
                        0,
                        0,
                        BigDecimal.ZERO
                )
        );
    }

    private List<StudentSeed> menteeSeeds() {
        return List.of(
                new StudentSeed("mentee01.demo@skillswap.local", "Pham Gia Bao", "SE210011", CampusCode.HCM, "CNTT", "CNTT_KTPM", 3, 2024, false, null, demoAvatarUrl("mentee01"), "Mình cần hỗ trợ làm dự án và chuẩn bị phỏng vấn thực tập."),
                new StudentSeed("mentee02.demo@skillswap.local", "Dang Kieu Linh", "SE210012", CampusCode.HA_NOI, "CNTT", "CNTT_TTNT", 4, 2024, false, null, demoAvatarUrl("mentee02"), "Muốn học thêm AI cơ bản và cách trình bày project."),
                new StudentSeed("mentee03.demo@skillswap.local", "Tran Bao Anh", "SE210013", CampusCode.DA_NANG, "QTKD", "QTKD_MKT", 2, 2024, false, null, demoAvatarUrl("mentee03"), "Cần định hướng nghề nghiệp và CV review."),
                new StudentSeed("mentee04.demo@skillswap.local", "Le Minh Khang", "SE210014", CampusCode.CAN_THO, "CTTT", "CTTT_TTDPM", 5, 2023, false, null, demoAvatarUrl("mentee04"), "Muốn luyện thuyết trình và review portfolio."),
                new StudentSeed("mentee05.demo@skillswap.local", "Nguyen Thu Trang", "SE210015", CampusCode.QUY_NHON, "NN", "NN_NNA", 4, 2024, false, null, demoAvatarUrl("mentee05"), "Cần mentor hỗ trợ môn học và phỏng vấn tiếng Anh."),
                new StudentSeed("mentee06.demo@skillswap.local", "Hoang Duc Thinh", "SE210016", CampusCode.HCM, "CNTT", "CNTT_HTTT", 6, 2023, false, null, demoAvatarUrl("mentee06"), "Mình cần học database và system analysis."),
                new StudentSeed("mentee07.demo@skillswap.local", "Vu Ngoc Ha", "SE210017", CampusCode.HA_NOI, "LUAT", "LUAT_L", 2, 2024, false, null, demoAvatarUrl("mentee07"), "Muốn hiểu cách viết báo cáo và thuyết trình."),
                new StudentSeed("mentee08.demo@skillswap.local", "Do Minh Quan", "SE210018", CampusCode.DA_NANG, "QTKD", "QTKD_TMDT", 1, 2024, false, null, demoAvatarUrl("mentee08"), "Cần mentor giúp xây kế hoạch học tập."),
                new StudentSeed("mentee09.demo@skillswap.local", "Bui Khanh Vy", "SE210019", CampusCode.CAN_THO, "CTTT", "CTTT_QHCC", 7, 2022, false, null, demoAvatarUrl("mentee09"), "Muốn review CV và chuẩn bị phỏng vấn internship."),
                new StudentSeed("mentee10.demo@skillswap.local", "Ngo Thanh Nhan", "SE210020", CampusCode.QUY_NHON, "CNTT", "CNTT_ATTT", 5, 2023, false, null, demoAvatarUrl("mentee10"), "Cần mentor giải đáp thắc mắc về an toàn thông tin.")
        );
    }

    private record StudentSeed(
            String email,
            String fullName,
            String studentCode,
            CampusCode campusCode,
            String programCode,
            String specializationCode,
            int semester,
            int intakeYear,
            boolean alumni,
            Integer graduationYear,
            String avatarUrl,
            String bio
    ) {
    }

    private record MentorSeed(
            String email,
            String fullName,
            String studentCode,
            CampusCode campusCode,
            String programCode,
            String specializationCode,
            int semester,
            int intakeYear,
            boolean alumni,
            Integer graduationYear,
            String headline,
            String expertiseDescription,
            String supportingSubjects,
            TeachingMode teachingMode,
            Integer sessionDuration,
            String avatarUrl,
            List<String> helpTopicCodes,
            String serviceTitle,
            String serviceDescription,
            Integer serviceDuration,
            boolean serviceFree,
            BigDecimal priceAmount,
            boolean activeMentor,
            Integer verifiedDaysAgo,
            Integer totalCompletedSessions,
            Integer totalReviews,
            BigDecimal averageRating
    ) {
        private StudentSeed toStudentSeed() {
            return new StudentSeed(email, fullName, studentCode, campusCode, programCode, specializationCode, semester, intakeYear, alumni, graduationYear, avatarUrl, expertiseDescription);
        }
    }

    private record SlotSeed(int dayOffset, int hour, int minute, int durationMinutes) {
    }
}
