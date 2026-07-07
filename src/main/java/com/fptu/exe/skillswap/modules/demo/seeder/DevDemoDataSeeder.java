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
import java.util.Collections;
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
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class DevDemoDataSeeder implements CommandLineRunner {

    private static final int MIN_SERVICE_PRICE_SCOIN_PER_MINUTE = 1_200;
    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final int TOTAL_MENTOR_COUNT = 100;
    private static final int MENTEE_COUNT = 10;
    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final int MIN_QUALIFIED_HCM_MENTORS = 30;
    private static final Set<VerificationStatus> OPEN_REQUEST_STATUSES = EnumSet.of(
            VerificationStatus.DRAFT,
            VerificationStatus.PENDING_REVIEW,
            VerificationStatus.NEEDS_REVISION
    );
    private static final List<String> VIETNAMESE_LAST_NAMES = List.of(
            "Nguyen", "Tran", "Le", "Pham", "Hoang", "Huynh", "Phan", "Vu", "Vo", "Dang",
            "Bui", "Do", "Ho", "Ngo", "Duong", "Ly", "Mai", "Dinh", "Truong", "Cao"
    );
    private static final List<String> VIETNAMESE_MIDDLE_NAMES = List.of(
            "Minh", "Gia", "Thanh", "Ngoc", "Quoc", "Bao", "Anh", "Duc", "Thu", "Tien",
            "Khanh", "Hoai", "Nhat", "Phuong", "Tu", "Xuan", "Yen", "Hai", "Lan", "My"
    );
    private static final List<String> VIETNAMESE_GIVEN_NAMES = List.of(
            "Khang", "Linh", "Huy", "Vy", "Quan", "Trang", "Phong", "Nhi", "Thinh", "Ha",
            "Dat", "Chau", "An", "Ngan", "Tam", "Hanh", "Kiet", "Quyen", "Son", "Truc",
            "Phuc", "Thao", "Long", "Nhu", "Lam", "Uyen", "Tai", "Yen", "Duy", "Quynh"
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

    private boolean seederEnabled = false;

    public void setSeederEnabled(boolean seederEnabled) {
        this.seederEnabled = seederEnabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seederEnabled) {
            log.info("SkillSwap demo data seeding is disabled to keep DB clean.");
            return;
        }

        log.info("Starting SkillSwap demo data seeding...");

        Map<String, Tag> helpTopics = loadHelpTopics();
        purgeMenteeSeeds();
        seedMentors(helpTopics);
        logQualifiedMentorStatistics();

        log.info("SkillSwap demo data seeding completed successfully!");
    }

    private void purgeMenteeSeeds() {
        for (StudentSeed seed : menteeSeeds()) {
            userRepository.findByEmailIncludingDeleted(seed.email()).ifPresent(user -> {
                studentProfileRepository.findWithDetailsByUserId(user.getId())
                        .ifPresent(studentProfileRepository::delete);
                userRepository.delete(user);
            });
            oauthAccountRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, demoProviderUserId(seed.email()))
                    .ifPresent(oauthAccountRepository::delete);
        }
    }

    private void seedMentors(Map<String, Tag> helpTopics) {
        for (MentorSeed seed : mentorSeeds()) {
            User user = upsertUser(seed.email(), seed.fullName(), seed.avatarUrl(), Set.of(RoleCode.MENTEE, RoleCode.MENTOR));
            upsertOauthAccount(user, demoProviderUserId(seed.email()));
            upsertStudentProfile(user, seed.toStudentSeed());

            MentorProfile mentorProfile = upsertMentorProfile(user, seed);
            upsertMentorTags(mentorProfile, seed.helpTopicCodes(), helpTopics);
            upsertMentorService(mentorProfile, seed, helpTopics);
            upsertAvailabilityPlan(mentorProfile);
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
        profile.setClaimedStudentCode(seed.studentCode());
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

        mentorTagRepository.deleteByIdMentorUserId(mentorProfile.getUserId());
        boolean primaryAssigned = false;
        for (String code : tagCodes) {
            Tag tag = tags.get(code);
            if (tag == null) {
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
            if (existing.size() > 1) {
                mentorServiceRepository.deleteAll(existing.subList(1, existing.size()));
            }
            service.setMentorProfile(mentorProfile);
            service.setTitle(seed.serviceTitle());
            service.setDescription(seed.serviceDescription());
            service.setExpectedOutcome("Sau buổi mentoring, mentee có checklist hành động rõ ràng để tự cải thiện.");
            service.setDurationMinutes(seed.serviceDuration());
            service.setFree(seed.serviceFree());
            service.setPriceScoin(normalizedServicePrice(seed.serviceFree(), seed.serviceDuration(), seed.priceScoin()));
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
                .expectedOutcome("Sau buổi mentoring, mentee có checklist hành động rõ ràng để tự cải thiện.")
                .durationMinutes(seed.serviceDuration())
                .isFree(seed.serviceFree())
                .priceScoin(normalizedServicePrice(seed.serviceFree(), seed.serviceDuration(), seed.priceScoin()))
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
        MentorAvailabilityRule activeRule;
        if (rules.isEmpty()) {
            activeRule = MentorAvailabilityRule.builder()
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
            activeRule = mentorAvailabilityRuleRepository.save(activeRule);
        } else {
            activeRule = rules.get(0);
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
                    .rule(activeRule)
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
                "HELP_STUDY_PLAN",
                "HELP_MAJOR_ORIENTATION",
                "HELP_CAREER_PATH",
                "HELP_INTERNSHIP",
                "HELP_CV_REVIEW",
                "HELP_INTERVIEW",
                "HELP_GRADUATION_THESIS",
                "HELP_FOREIGN_LANGUAGE",
                "HELP_CAMPUS_LIFE",
                "HELP_INFORMATION",
                "HELP_QA",
                "HELP_PROJECT_REVIEW"
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

    private Integer normalizedServicePrice(boolean serviceFree, Integer durationMinutes, Integer configuredPriceScoin) {
        if (serviceFree) {
            return 0;
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalStateException("Demo mentor service duration must be positive");
        }
        int minimumPrice = durationMinutes * MIN_SERVICE_PRICE_SCOIN_PER_MINUTE;
        int configured = configuredPriceScoin == null ? 0 : configuredPriceScoin;
        return Math.max(configured, minimumPrice);
    }

    private List<MentorSeed> mentorSeeds() {
        List<MentorSeed> seeds = new ArrayList<>();

        seeds.addAll(buildMentorGroup(
                "ktpm-hcm",
                "KTPM HCM",
                CampusCode.HCM,
                "CNTT",
                "CNTT_KTPM",
                20,
                1,
                List.of("HELP_CV_REVIEW", "HELP_INTERVIEW", "HELP_QA"),
                "Spring Boot Mentor",
                "Backend, Spring Boot, REST API, PostgreSQL, Docker, clean architecture",
                "EXE101, EXE201, PRJ301",
                TeachingMode.ONLINE,
                60,
                false,
                120,
                1L
        ));

        seeds.addAll(buildMentorGroup(
                "ttdpt-hcm",
                "TTDPT HCM",
                CampusCode.HCM,
                "CTTT",
                "CTTT_TTDPM",
                10,
                21,
                List.of("HELP_PROJECT_REVIEW", "HELP_INFORMATION", "HELP_QA"),
                "Communication Mentor",
                "Presentation, storytelling, teamwork, UX explanation, demo pitching",
                "COM101, COM102, PRJ301",
                TeachingMode.HYBRID,
                90,
                false,
                100,
                21L
        ));

        seeds.addAll(buildMentorGroup(
                "ai-hcm",
                "AI HCM",
                CampusCode.HCM,
                "CNTT",
                "CNTT_TTNT",
                10,
                31,
                List.of("HELP_CV_REVIEW", "HELP_INTERVIEW", "HELP_STUDY_PLAN"),
                "AI Mentor",
                "Machine learning, Python, data processing, model review, project guidance",
                "AI100, ML101, DSA",
                TeachingMode.ONLINE,
                90,
                false,
                180,
                41L
        ));

        seeds.addAll(buildMentorGroup(
                "tkmts-hcm",
                "TKMTS HCM",
                CampusCode.HCM,
                "CNTT",
                "CNTT_TKDHMT",
                10,
                41,
                List.of("HELP_PROJECT_REVIEW", "HELP_INFORMATION", "HELP_QA"),
                "Design Mentor",
                "UI design, visual storytelling, product demo, frontend presentation, design review",
                "WEB101, UIX201, PRJ301",
                TeachingMode.HYBRID,
                60,
                false,
                110,
                61L
        ));

        seeds.addAll(buildMentorGroup(
                "biz-intl-hcm",
                "BUS HCM",
                CampusCode.HCM,
                "QTKD",
                "QTKD_KDQT",
                10,
                51,
                List.of("HELP_CAREER_PATH", "HELP_INTERNSHIP", "HELP_QA"),
                "International Business Mentor",
                "Business strategy, market analysis, internship guidance, communication",
                "BUS101, MKT201, COM102",
                TeachingMode.ONLINE,
                60,
                false,
                130,
                81L
        ));

        seeds.addAll(buildRandomMentors(40, 61));

        if (seeds.size() != TOTAL_MENTOR_COUNT) {
            throw new IllegalStateException("Demo mentor seed count must be exactly " + TOTAL_MENTOR_COUNT + ", but was " + seeds.size());
        }
        return seeds;
    }

    private List<MentorSeed> buildMentorGroup(
            String emailPrefix,
            String fullNamePrefix,
            CampusCode campusCode,
            String programCode,
            String specializationCode,
            int count,
            int startIndex,
            List<String> helpTopicCodes,
            String headlinePrefix,
            String expertisePrefix,
            String supportingSubjectsPrefix,
            TeachingMode teachingMode,
            Integer sessionDuration,
            boolean serviceFree,
            int basePriceScoin,
            long verifiedDaysStart
    ) {
        List<MentorSeed> seeds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int seedIndex = startIndex + i - 1;
            boolean alumni = i % 4 == 0;
            Integer graduationYear = alumni ? 2024 - (i % 3) : null;
            String suffix = String.format("%02d", seedIndex);
            String email = String.format("mentor%02d.demo@skillswap.local", seedIndex);
            String fullName = vietnameseFullName(seedIndex);
            String studentCode = String.format("SE22%04d", seedIndex);
            String headline = specializedHeadline(programCode, specializationCode, seedIndex, alumni);
            String expertiseDescription = specializedExpertiseDescription(programCode, specializationCode, seedIndex, alumni);
            String supportingSubjects = specializedSupportingSubjects(programCode, specializationCode, seedIndex);
            String serviceTitle = specializedServiceTitle(programCode, specializationCode, seedIndex);
            String serviceDescription = specializedServiceDescription(programCode, specializationCode, seedIndex);
            Integer priceScoin = normalizedServicePrice(serviceFree, sessionDuration, basePriceScoin + ((i - 1) * 5));
            int totalCompletedSessions = 10 + i;
            int totalReviews = 6 + (i % 12);
            BigDecimal averageRating = BigDecimal.valueOf(450L - ((i - 1L) % 10L) * 5L, 2);

            seeds.add(new MentorSeed(
                    email,
                    fullName,
                    studentCode,
                    campusCode,
                    programCode,
                    specializationCode,
                    alumni ? 0 : 6 + (i % 3),
                    2020 + (i % 5),
                    alumni,
                    graduationYear,
                    headlinePrefix + " " + suffix,
                    expertisePrefix,
                    supportingSubjectsPrefix,
                    teachingMode,
                    sessionDuration,
                    demoAvatarUrl("mentor" + suffix),
                    helpTopicCodes,
                    serviceTitle,
                    serviceDescription,
                    sessionDuration,
                    serviceFree,
                    priceScoin,
                    true,
                    Math.toIntExact(verifiedDaysStart + i),
                    totalCompletedSessions,
                    totalReviews,
                    averageRating
            ).withProfileContent(fullName, headline, expertiseDescription, supportingSubjects));
        }
        return seeds;
    }

    private List<MentorSeed> buildRandomMentors(int count, int startIndex) {
        List<CampusCode> campuses = new ArrayList<>(List.of(
                CampusCode.HA_NOI,
                CampusCode.DA_NANG,
                CampusCode.CAN_THO,
                CampusCode.QUY_NHON
        ));
        Collections.shuffle(campuses, new java.util.Random(20260617L));

        List<RandomTrack> tracks = new ArrayList<>(List.of(
                new RandomTrack("CNTT", "CNTT_ATTT", List.of("HELP_CV_REVIEW", "HELP_QA"), "Security Mentor", "Information security, secure coding, system hardening", "SEC101, DSA, EXE101", TeachingMode.ONLINE, 60, false, 95),
                new RandomTrack("CNTT", "CNTT_HTTT", List.of("HELP_PROJECT_REVIEW", "HELP_QA"), "System Analysis Mentor", "Requirements, database design, UML, architecture review", "DB101, UML201, PRJ301", TeachingMode.HYBRID, 60, false, 105),
                new RandomTrack("CNTT", "CNTT_TTNT", List.of("HELP_INTERVIEW", "HELP_STUDY_PLAN"), "AI Mentor", "Machine learning, Python, data preparation, portfolio review", "AI100, ML101, DSA", TeachingMode.ONLINE, 90, false, 175),
                new RandomTrack("CTTT", "CTTT_QHCC", List.of("HELP_INTERVIEW", "HELP_PROJECT_REVIEW"), "Communication Mentor", "Presentation, teamwork, pitching, public speaking", "COM101, COM102, PRJ301", TeachingMode.OFFLINE, 90, false, 90),
                new RandomTrack("NN", "NN_NNA", List.of("HELP_CAREER_PATH", "HELP_INTERNSHIP"), "English Mentor", "English communication, interview practice, speaking confidence", "ENG101, ENG201, COM102", TeachingMode.ONLINE, 60, false, 115),
                new RandomTrack("LUAT", "LUAT_LKT", List.of("HELP_QA", "HELP_STUDY_PLAN"), "Law Mentor", "Legal studies, documentation, presentation structure, career advice", "LAW101, COM102, EXE101", TeachingMode.ONLINE, 60, false, 80),
                new RandomTrack("QTKD", "QTKD_MKT", List.of("HELP_CAREER_PATH", "HELP_INTERNSHIP"), "Business Mentor", "Marketing, business analysis, internship prep, communication", "BUS101, MKT201, COM102", TeachingMode.HYBRID, 60, false, 100),
                new RandomTrack("QTKD", "QTKD_TMDT", List.of("HELP_PROJECT_REVIEW", "HELP_QA"), "E-commerce Mentor", "E-commerce, product review, project storytelling, digital business", "ECOM101, PRJ301, COM102", TeachingMode.ONLINE, 60, false, 100)
        ));
        Collections.shuffle(tracks, new java.util.Random(20260618L));

        List<MentorSeed> seeds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int seedIndex = startIndex + i - 1;
            CampusCode campusCode = campuses.get((i - 1) % campuses.size());
            RandomTrack track = tracks.get((i - 1) % tracks.size());
            String suffix = String.format("%02d", seedIndex);
            String fullName = vietnameseFullName(seedIndex);
            String headline = specializedHeadline(track.programCode(), track.specializationCode(), seedIndex, i % 5 == 0);
            String expertiseDescription = specializedExpertiseDescription(track.programCode(), track.specializationCode(), seedIndex, i % 5 == 0);
            String supportingSubjects = specializedSupportingSubjects(track.programCode(), track.specializationCode(), seedIndex);
            String serviceTitle = specializedServiceTitle(track.programCode(), track.specializationCode(), seedIndex);
            String serviceDescription = specializedServiceDescription(track.programCode(), track.specializationCode(), seedIndex);

            seeds.add(new MentorSeed(
                    String.format("mentor%02d.demo@skillswap.local", seedIndex),
                    fullName,
                    String.format("SE22%04d", seedIndex),
                    campusCode,
                    track.programCode(),
                    track.specializationCode(),
                    i % 5 == 0 ? 0 : 6 + (i % 3),
                    2020 + (seedIndex % 5),
                    i % 5 == 0,
                    i % 5 == 0 ? 2023 - (i % 2) : null,
                    headline,
                    expertiseDescription,
                    supportingSubjects,
                    track.teachingMode(),
                    track.sessionDuration(),
                    demoAvatarUrl("mentor" + suffix),
                    track.helpTopicCodes(),
                    serviceTitle,
                    serviceDescription,
                    track.sessionDuration(),
                    track.serviceFree(),
                    normalizedServicePrice(track.serviceFree(), track.sessionDuration(), track.basePriceScoin() + ((i - 1) * 3)),
                    true,
                    120 + i,
                    5 + i,
                    3 + (i % 10),
                    BigDecimal.valueOf(430L - ((i - 1L) % 6L) * 6L, 2)
            ));
        }
        return seeds;
    }

    private void logQualifiedMentorStatistics() {
        List<MentorProfile> activeMentors = mentorProfileRepository.findByStatus(MentorStatus.ACTIVE);
        Map<UUID, StudentProfile> studentProfiles = studentProfileRepository.findAll().stream()
                .filter(profile -> profile.getUser() != null && profile.getUser().getId() != null)
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), profile -> profile, (left, right) -> left));

        List<MentorProfile> qualifiedMentors = activeMentors.stream()
                .filter(this::isQualifiedDiscoverableMentor)
                .filter(profile -> {
                    StudentProfile studentProfile = studentProfiles.get(profile.getUserId());
                    return studentProfile != null
                            && studentProfile.getCampus() != null
                            && studentProfile.getCampus().getCode() == CampusCode.HCM;
                })
                .toList();

        if (qualifiedMentors.size() < MIN_QUALIFIED_HCM_MENTORS) {
            throw new IllegalStateException("Demo data must contain at least " + MIN_QUALIFIED_HCM_MENTORS
                    + " qualified HCM mentors, but found " + qualifiedMentors.size());
        }

        Map<String, Long> campusStats = qualifiedMentors.stream()
                .map(profile -> studentProfiles.get(profile.getUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        profile -> profile.getCampus().getCode().name(),
                        java.util.TreeMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> programStats = qualifiedMentors.stream()
                .map(profile -> studentProfiles.get(profile.getUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        profile -> profile.getProgram() == null ? "UNKNOWN" : profile.getProgram().getCode(),
                        java.util.TreeMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> specializationStats = qualifiedMentors.stream()
                .map(profile -> studentProfiles.get(profile.getUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        profile -> profile.getSpecialization() == null ? "UNKNOWN" : profile.getSpecialization().getCode(),
                        java.util.TreeMap::new,
                        Collectors.counting()
                ));

        log.info("Qualified HCM mentors after seed: {}", qualifiedMentors.size());
        log.info("Qualified mentor stats by campus: {}", campusStats);
        log.info("Qualified mentor stats by program: {}", programStats);
        log.info("Qualified mentor stats by specialization: {}", specializationStats);
    }

    private boolean isQualifiedDiscoverableMentor(MentorProfile profile) {
        return profile != null
                && profile.getUser() != null
                && profile.getUser().getStatus() == UserStatus.ACTIVE
                && profile.getStatus() == MentorStatus.ACTIVE
                && profile.getVerifiedAt() != null
                && profile.isAvailable()
                && profile.getTeachingMode() != null
                && profile.getSessionDuration() != null
                && hasText(profile.getHeadline())
                && hasText(profile.getExpertiseDescription())
                && hasText(profile.getSupportingSubjects())
                && !mentorTagRepository.findByIdMentorUserIdAndIdTagTypeIn(profile.getUserId(), List.of(MentorTagType.HELP_TOPIC)).isEmpty();
    }

    private String vietnameseFullName(int seedIndex) {
        String lastName = VIETNAMESE_LAST_NAMES.get(seedIndex % VIETNAMESE_LAST_NAMES.size());
        String middleName = VIETNAMESE_MIDDLE_NAMES.get((seedIndex * 3) % VIETNAMESE_MIDDLE_NAMES.size());
        String givenName = VIETNAMESE_GIVEN_NAMES.get((seedIndex * 7) % VIETNAMESE_GIVEN_NAMES.size());
        return lastName + " " + middleName + " " + givenName;
    }

    private String specializedHeadline(String programCode, String specializationCode, int seedIndex, boolean alumni) {
        return switch (specializationCode) {
            case "CNTT_KTPM" -> alumni
                    ? "Alumni backend mentor | Spring Boot, PostgreSQL, Docker"
                    : keywordVariant(seedIndex,
                            "Mentor backend Java | Spring Boot, REST API, SWP391",
                            "Mentor fullstack | React, Spring Boot, database design",
                            "Mentor project | OJT, PRJ301, clean architecture");
            case "CNTT_TTNT" -> keywordVariant(seedIndex,
                    "Mentor AI | Python, machine learning, data preprocessing",
                    "Mentor tri tue nhan tao | model review, MLOps co ban, portfolio AI",
                    "Mentor data science | Pandas, notebook, project AI");
            case "CNTT_HTTT" -> keywordVariant(seedIndex,
                    "Mentor he thong thong tin | database, UML, BA co ban",
                    "Mentor phan tich he thong | database design, ERD, SQL",
                    "Mentor database | requirements, system analysis, documentation");
            case "CNTT_ATTT" -> keywordVariant(seedIndex,
                    "Mentor an toan thong tin | secure coding, OWASP, network basics",
                    "Mentor security | pentest co ban, authentication, logging",
                    "Mentor cyber security | secure API, risk review, SOC mindset");
            case "CNTT_TKDHMT" -> keywordVariant(seedIndex,
                    "Mentor UI/UX | Figma, design system, presentation deck",
                    "Mentor thiet ke do hoa so | UI review, portfolio, storytelling",
                    "Mentor san pham so | React UI, UX writing, visual critique");
            case "CTTT_TTDPM" -> keywordVariant(seedIndex,
                    "Mentor multimedia | React, storytelling, pitching, demo day",
                    "Mentor presentation | content plan, communication, UX explanation",
                    "Mentor truyền thông số | product demo, public speaking, teamwork");
            case "QTKD_KDQT" -> keywordVariant(seedIndex,
                    "Mentor kinh doanh quốc tế | case analysis, internship prep",
                    "Mentor business | market research, communication, CV review",
                    "Mentor career | networking, OJT mindset, business presentation");
            case "QTKD_MKT" -> keywordVariant(seedIndex,
                    "Mentor digital marketing | content, campaign review, analytics",
                    "Mentor marketing | brand pitch, customer insight, CV review",
                    "Mentor growth | research, internship prep, presentation");
            case "QTKD_TMDT" -> keywordVariant(seedIndex,
                    "Mentor thương mại điện tử | e-commerce, project review, SQL cơ bản",
                    "Mentor e-commerce | product flow, analytics, pitching",
                    "Mentor digital business | PRJ301, idea validation, feedback");
            case "NN_NNA" -> keywordVariant(seedIndex,
                    "Mentor tiếng Anh | interview, speaking, presentation",
                    "Mentor English communication | CV, mock interview, confidence",
                    "Mentor language | study plan, speaking, career support");
            case "LUAT_LKT" -> keywordVariant(seedIndex,
                    "Mentor luật kinh tế | legal writing, report structure, presentation",
                    "Mentor law | business law basics, argumentation, thesis support",
                    "Mentor học thuật | documentation, critical thinking, defense");
            default -> switch (programCode) {
                case "CNTT" -> "Mentor công nghệ | project review, backend, database";
                case "CTTT" -> "Mentor truyền thông | content, pitching, collaboration";
                case "QTKD" -> "Mentor kinh doanh | internship, CV, communication";
                default -> "Mentor SkillSwap | hỗ trợ môn học và định hướng";
            };
        };
    }

    private String specializedExpertiseDescription(String programCode, String specializationCode, int seedIndex, boolean alumni) {
        String intro = alumni
                ? "Mình là alumni FPT đã đi làm và thường hỗ trợ sinh viên chuẩn bị internship, OJT và project học kỳ."
                : "Mình là mentor đang theo học hoặc vừa hoàn thành các học kỳ chuyên ngành tại FPT, quen với cách chấm project và review báo cáo.";

        return switch (specializationCode) {
            case "CNTT_KTPM" -> intro + " Mình mạnh về Spring Boot, REST API, PostgreSQL, Docker và clean architecture. Có thể hỗ trợ các môn như EXE101, EXE201, SWP391, PRJ301, code review và tối ưu database.";
            case "CNTT_TTNT" -> intro + " Mình tập trung vào Python, machine learning, data preprocessing và cách trình bày project AI rõ ràng. Có thể hỗ trợ portfolio AI, review notebook, model baseline và báo cáo thực nghiệm.";
            case "CNTT_HTTT" -> intro + " Mình hỗ trợ database design, SQL, UML, requirements và system analysis. Phù hợp cho bạn đang làm đồ án cần ERD, use case, sequence diagram hoặc chuẩn bị bảo vệ proposal.";
            case "CNTT_ATTT" -> intro + " Mình hỗ trợ secure coding, authentication, logging, OWASP và tư duy threat modeling cơ bản. Hợp với bạn muốn học backend an toàn hoặc làm project có yếu tố bảo mật.";
            case "CNTT_TKDHMT" -> intro + " Mình hỗ trợ UI/UX, Figma, design critique, storytelling và cách kết nối giữa design với frontend React. Có thể review portfolio, case study và cấu trúc trình bày sản phẩm.";
            case "CTTT_TTDPM" -> intro + " Mình hỗ trợ thuyết trình, storytelling, demo pitching và phối hợp nội dung cho project liên ngành. Hợp với các bạn cần luyện trình bày đồ án, bảo vệ project hoặc demo day.";
            case "QTKD_KDQT" -> intro + " Mình hỗ trợ market analysis, business presentation, networking, CV và định hướng internship. Có thể review slide, assignment và tình huống thực tế trong môi trường doanh nghiệp.";
            case "QTKD_MKT" -> intro + " Mình hỗ trợ content planning, campaign thinking, customer insight và CV cho ngành marketing. Hợp với bạn cần góp ý proposal, deck, case study hoặc định hướng thực tập.";
            case "QTKD_TMDT" -> intro + " Mình hỗ trợ e-commerce flow, phân tích sản phẩm, idea validation và cách trình bày project kinh doanh số. Có thể review assignment, phản biện logic và luyện pitching.";
            case "NN_NNA" -> intro + " Mình hỗ trợ speaking, mock interview, CV tiếng Anh và kỹ năng trình bày học thuật. Hợp với bạn muốn tăng tự tin khi phỏng vấn hoặc thuyết trình trước hội đồng.";
            case "LUAT_LKT" -> intro + " Mình hỗ trợ legal writing, lập luận, cấu trúc báo cáo và cách trình bày case. Hợp với bạn cần định hướng môn học, phản biện nội dung hoặc chuẩn bị bảo vệ bài làm.";
            default -> intro + " Mình có thể hỗ trợ review bài tập, giải đáp thắc mắc, định hướng môn học và góp ý project theo bối cảnh FPT.";
        };
    }

    private String specializedSupportingSubjects(String programCode, String specializationCode, int seedIndex) {
        return switch (specializationCode) {
            case "CNTT_KTPM" -> keywordVariant(seedIndex,
                    "EXE101, EXE201, SWP391, PRJ301, Spring Boot, PostgreSQL, Docker",
                    "OJT, PRJ301, React, Spring Boot, REST API, database design",
                    "Java backend, Clean Architecture, CI/CD cơ bản, code review");
            case "CNTT_TTNT" -> keywordVariant(seedIndex,
                    "Python, Machine Learning, AI100, ML101, data preprocessing",
                    "Model evaluation, notebook review, portfolio AI, Pandas",
                    "Deep learning cơ bản, project AI, data storytelling");
            case "CNTT_HTTT" -> keywordVariant(seedIndex,
                    "Database Design, SQL, UML201, system analysis, BA cơ bản",
                    "ERD, sequence diagram, use case, report structure",
                    "Requirements, documentation, PRJ301, architecture review");
            case "CNTT_ATTT" -> keywordVariant(seedIndex,
                    "Secure coding, OWASP, authentication, JWT, logging",
                    "Network basics, API security, risk review, backend security",
                    "System hardening, threat modeling, security checklist");
            case "CNTT_TKDHMT" -> keywordVariant(seedIndex,
                    "Figma, UI/UX critique, design system, portfolio",
                    "React UI, presentation deck, case study, visual storytelling",
                    "Prototype review, typography, color system, product demo");
            case "CTTT_TTDPM" -> keywordVariant(seedIndex,
                    "COM101, COM102, PRJ301, storytelling, pitching",
                    "Presentation, teamwork, demo script, public speaking",
                    "Content planning, UX explanation, stage confidence");
            case "QTKD_KDQT" -> keywordVariant(seedIndex,
                    "BUS101, MKT201, communication, internship prep",
                    "Case analysis, business presentation, CV review, OJT mindset",
                    "Market research, networking, slide review, report critique");
            case "QTKD_MKT" -> keywordVariant(seedIndex,
                    "Marketing plan, customer insight, content review, campaign critique",
                    "Brand storytelling, proposal review, CV, internship support",
                    "Analytics cơ bản, pitch deck, communication");
            case "QTKD_TMDT" -> keywordVariant(seedIndex,
                    "E-commerce, product flow, PRJ301, business analytics",
                    "Proposal review, idea validation, pitching, report structure",
                    "Digital business, customer journey, feedback presentation");
            case "NN_NNA" -> keywordVariant(seedIndex,
                    "English speaking, mock interview, CV tiếng Anh",
                    "Presentation, confidence, study guidance, communication",
                    "Listening-speaking, internship interview, pronunciation");
            case "LUAT_LKT" -> keywordVariant(seedIndex,
                    "Legal writing, report structure, argumentation, presentation",
                    "Business law basics, case reading, thesis support",
                    "Study guidance, documentation, defense preparation");
            default -> switch (programCode) {
                case "CNTT" -> "Project review, backend, database, giải đáp thắc mắc";
                case "QTKD" -> "CV review, internship support, business presentation";
                default -> "Hướng dẫn môn học, giải đáp thắc mắc, review project";
            };
        };
    }

    private String specializedServiceTitle(String programCode, String specializationCode, int seedIndex) {
        return switch (specializationCode) {
            case "CNTT_KTPM" -> keywordVariant(seedIndex,
                    "Review đồ án backend Spring Boot và database",
                    "Mentoring OJT, SWP391 và project fullstack React + Spring",
                    "Code review Java backend và clean architecture");
            case "CNTT_TTNT" -> keywordVariant(seedIndex,
                    "Review project AI và portfolio machine learning",
                    "Mentoring Python, data preprocessing và model baseline",
                    "Hỗ trợ report, notebook và thuyết trình project AI");
            case "CNTT_HTTT" -> keywordVariant(seedIndex,
                    "Review database, UML và system analysis",
                    "Mentoring requirements, ERD và báo cáo đồ án",
                    "Hỗ trợ PRJ301, SQL và kiến trúc hệ thống");
            case "CNTT_TKDHMT" -> keywordVariant(seedIndex,
                    "Review portfolio UI/UX và case study",
                    "Mentoring Figma, React UI và product storytelling",
                    "Góp ý design system và bài thuyết trình sản phẩm");
            case "CTTT_TTDPM" -> keywordVariant(seedIndex,
                    "Luyện pitching và demo presentation",
                    "Góp ý storytelling, teamwork và nội dung demo",
                    "Review slide, script và kỹ năng đứng trình bày");
            default -> keywordVariant(seedIndex,
                    "Mentoring định hướng môn học và review project",
                    "Hỗ trợ internship, CV và giải đáp thắc mắc",
                    "Góp ý assignment, báo cáo và kỹ năng trình bày");
        };
    }

    private String specializedServiceDescription(String programCode, String specializationCode, int seedIndex) {
        return switch (specializationCode) {
            case "CNTT_KTPM" -> "Buổi mentoring tập trung vào Spring Boot, database, SWP391, OJT hoặc review code Java backend. Mentee có thể mang source code, ERD hoặc backlog để được góp ý thực tế.";
            case "CNTT_TTNT" -> "Buổi mentoring tập trung vào project AI, Python, data cleaning và cách trình bày kết quả mô hình. Phù hợp cho bạn cần review notebook, baseline hoặc portfolio học máy.";
            case "CNTT_HTTT" -> "Buổi mentoring tập trung vào SQL, database design, UML và logic nghiệp vụ của đồ án. Phù hợp khi bạn cần rà ERD, use case hoặc report system analysis.";
            case "CNTT_TKDHMT" -> "Buổi mentoring tập trung vào Figma, UI critique, case study và cách kể chuyện sản phẩm. Có thể review portfolio, prototype hoặc màn hình React UI.";
            case "CTTT_TTDPM" -> "Buổi mentoring tập trung vào storytelling, pitching, script và cách phối hợp nhóm để demo thuyết phục hơn. Hợp với bạn chuẩn bị bảo vệ project hoặc làm presentation quan trọng.";
            default -> "Buổi mentoring tập trung vào giải đáp thắc mắc, review project, định hướng môn học và góp ý tài liệu thực tế theo bối cảnh FPT.";
        };
    }

    private String keywordVariant(int seedIndex, String first, String second, String third) {
        return switch (Math.floorMod(seedIndex, 3)) {
            case 0 -> first;
            case 1 -> second;
            default -> third;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record RandomTrack(
            String programCode,
            String specializationCode,
            List<String> helpTopicCodes,
            String headlinePrefix,
            String expertisePrefix,
            String supportingSubjectsPrefix,
            TeachingMode teachingMode,
            Integer sessionDuration,
            boolean serviceFree,
            int basePriceScoin
    ) {
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
            Integer priceScoin,
            boolean activeMentor,
            Integer verifiedDaysAgo,
            Integer totalCompletedSessions,
            Integer totalReviews,
            BigDecimal averageRating
    ) {
        private MentorSeed withProfileContent(
                String fullName,
                String headline,
                String expertiseDescription,
                String supportingSubjects
        ) {
            return new MentorSeed(
                    email,
                    fullName,
                    studentCode,
                    campusCode,
                    programCode,
                    specializationCode,
                    semester,
                    intakeYear,
                    alumni,
                    graduationYear,
                    headline,
                    expertiseDescription,
                    supportingSubjects,
                    teachingMode,
                    sessionDuration,
                    avatarUrl,
                    helpTopicCodes,
                    serviceTitle,
                    serviceDescription,
                    serviceDuration,
                    serviceFree,
                    priceScoin,
                    activeMentor,
                    verifiedDaysAgo,
                    totalCompletedSessions,
                    totalReviews,
                    averageRating
            );
        }

        private StudentSeed toStudentSeed() {
            return new StudentSeed(email, fullName, studentCode, campusCode, programCode, specializationCode, semester, intakeYear, alumni, graduationYear, avatarUrl, expertiseDescription);
        }
    }

    private record SlotSeed(int dayOffset, int hour, int minute, int durationMinutes) {
    }
}
